#include <jni.h>
#include <string>
#include <vector>
#include <cmath>
#include <algorithm>
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "AutotunerNative", __VA_ARGS__)
#define PI 3.14159265358979323846

const int SAMPLE_RATE = 44100;
const int FRAME_SIZE = 1024; // Reduced frame size for better stability

// Musical notes (C4 to C5)
const std::vector<double> NOTES = {
    261.63, // C4
    277.18, // C#4
    293.66, // D4
    311.13, // D#4
    329.63, // E4
    349.23, // F4
    369.99, // F#4
    392.00, // G4
    415.30, // G#4
    440.00, // A4
    466.16, // A#4
    493.88, // B4
    523.25  // C5
};

// Find closest note frequency
double findClosestNote(double frequency)
{
  if (frequency <= 0)
    return frequency;

  double closestNote = NOTES[0];
  double minDiff = std::abs(frequency - NOTES[0]);

  for (double note : NOTES)
  {
    double diff = std::abs(frequency - note);
    if (diff < minDiff)
    {
      minDiff = diff;
      closestNote = note;
    }
  }

  return closestNote;
}

// Simple pitch detection using autocorrelation
double detectPitch(const std::vector<double> &buffer)
{
  std::vector<double> r(FRAME_SIZE);
  double maxCorrelation = 0.0;
  int maxLag = 0;

  // Calculate autocorrelation
  for (int lag = 0; lag < FRAME_SIZE / 2; lag++)
  {
    double sum = 0.0;
    double energy = 0.0;

    for (int i = 0; i < FRAME_SIZE - lag; i++)
    {
      sum += buffer[i] * buffer[i + lag];
      if (lag == 0)
      {
        energy += buffer[i] * buffer[i];
      }
    }

    // Normalize
    if (lag == 0)
    {
      r[lag] = 1.0;
      maxCorrelation = 1.0;
    }
    else if (energy > 0)
    {
      r[lag] = sum / energy;

      if (r[lag] > maxCorrelation)
      {
        maxCorrelation = r[lag];
        maxLag = lag;
      }
    }
  }

  // Check if we found a good correlation peak
  if (maxCorrelation > 0.5 && maxLag > 0)
  {
    return static_cast<double>(SAMPLE_RATE) / maxLag;
  }

  return 0.0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio_1recorder_AudioProcessor_processAudio(
    JNIEnv *env,
    jobject /* this */,
    jshortArray input,
    jshortArray output)
{
  jsize length = env->GetArrayLength(input);
  jshort *inputBuffer = env->GetShortArrayElements(input, nullptr);
  jshort *outputBuffer = env->GetShortArrayElements(output, nullptr);

  std::vector<double> processBuffer(FRAME_SIZE);
  std::vector<double> outputTemp(FRAME_SIZE);

  int offset = 0;
  while (offset + FRAME_SIZE <= length) // Process full frames
  {
    // Zero out buffers
    std::fill(processBuffer.begin(), processBuffer.end(), 0.0);
    std::fill(outputTemp.begin(), outputTemp.end(), 0.0);

    // Convert frame to double
    for (int i = 0; i < FRAME_SIZE; i++)
    {
      processBuffer[i] = inputBuffer[offset + i] / 32768.0;
    }

    // Detect pitch
    double currentPitch = detectPitch(processBuffer);

    if (currentPitch > 50.0 && currentPitch < 2000.0)
    {
      double targetPitch = findClosestNote(currentPitch);
      double ratio = targetPitch / currentPitch;

      if (std::abs(ratio - 1.0) > 0.01)
      {
        for (int i = 0; i < FRAME_SIZE; i++)
        {
          double pos = i * ratio;
          int pos1 = static_cast<int>(floor(pos));
          int pos2 = std::min(pos1 + 1, FRAME_SIZE - 1);
          double frac = pos - pos1;

          outputTemp[i] = processBuffer[pos1] * (1.0 - frac) +
                          processBuffer[pos2] * frac;
        }

        for (int i = 0; i < FRAME_SIZE; i++)
        {
          processBuffer[i] = outputTemp[i];
        }
      }
    }

    // Convert back to short
    for (int i = 0; i < FRAME_SIZE; i++)
    {
      double sample = processBuffer[i] * 32768.0;
      sample = std::max(-32768.0, std::min(32767.0, sample));
      outputBuffer[offset + i] = static_cast<short>(round(sample));
    }

    offset += FRAME_SIZE;
  }

  // **Handle the remaining unprocessed samples (last partial frame)**
  if (offset < length)
  {
    int remainingSamples = length - offset;
    std::vector<double> lastBuffer(remainingSamples);
    std::vector<double> lastOutput(remainingSamples);

    // Convert the remaining samples to double
    for (int i = 0; i < remainingSamples; i++)
    {
      lastBuffer[i] = inputBuffer[offset + i] / 32768.0;
    }

    // Process last chunk (without pitch correction)
    for (int i = 0; i < remainingSamples; i++)
    {
      lastOutput[i] = lastBuffer[i];
    }

    // Convert back to short and copy to output buffer
    for (int i = 0; i < remainingSamples; i++)
    {
      double sample = lastOutput[i] * 32768.0;
      sample = std::max(-32768.0, std::min(32767.0, sample));
      outputBuffer[offset + i] = static_cast<short>(round(sample));
    }
  }

  // **Ensure everything is saved properly**
  env->ReleaseShortArrayElements(input, inputBuffer, JNI_ABORT);
  env->ReleaseShortArrayElements(output, outputBuffer, JNI_COMMIT); // <-- Explicit commit

  LOGI("Processed %d samples successfully (length: %d)", offset, length);
}