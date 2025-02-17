import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Audio Recorder',
      theme: ThemeData(primarySwatch: Colors.blue),
      home: const AudioRecorderPage(),
    );
  }
}

class AudioRecorderPage extends StatefulWidget {
  const AudioRecorderPage({Key? key}) : super(key: key);

  @override
  State<AudioRecorderPage> createState() => _AudioRecorderPageState();
}

class _AudioRecorderPageState extends State<AudioRecorderPage> {
  static const platform = MethodChannel('com.example.audio_recorder/audio');

  bool _isRecording = false;
  bool _isPlaying = false;
  bool _playAutotuned = true; // Default to auto-tuned version
  String _recordingPath = '';
  String _errorMessage = '';
  bool _hasRecording = false;

  @override
  void initState() {
    super.initState();
    _setupMethodCallHandler();
  }

  void _setupMethodCallHandler() {
    platform.setMethodCallHandler((call) async {
      print('Received method call: ${call.method}');
      switch (call.method) {
        case 'onPlaybackComplete':
          setState(() {
            _isPlaying = false;
            _errorMessage = '';
          });
          break;
        case 'onPlaybackError':
          setState(() {
            _isPlaying = false;
            _errorMessage =
                call.arguments?.toString() ?? 'Unknown playback error';
          });
          _showError(_errorMessage);
          break;
      }
    });
  }

  Future<void> _startRecording() async {
    try {
      final String result = await platform.invokeMethod('startRecording');
      setState(() {
        _isRecording = true;
        _recordingPath = result;
        _errorMessage = '';
        _hasRecording = false;
      });
    } on PlatformException catch (e) {
      _showError('Failed to start recording: ${e.message}');
      setState(() {
        _isRecording = false;
        _errorMessage = e.message ?? 'Unknown error';
      });
    }
  }

  Future<void> _stopRecording() async {
    try {
      await platform.invokeMethod('stopRecording');
      setState(() {
        _isRecording = false;
        _errorMessage = '';
        _hasRecording = true;
      });
    } on PlatformException catch (e) {
      _showError('Failed to stop recording: ${e.message}');
      setState(() {
        _isRecording = false;
        _errorMessage = e.message ?? 'Unknown error';
      });
    }
  }

  Future<void> _startPlaying() async {
    if (!_hasRecording) {
      _showError('No recording available');
      return;
    }

    try {
      await platform.invokeMethod('startPlaying', {
        'useAutotuned': _playAutotuned,
      });
      setState(() {
        _isPlaying = true;
        _errorMessage = '';
      });
    } on PlatformException catch (e) {
      _showError('Failed to play recording: ${e.message}');
      setState(() {
        _isPlaying = false;
        _errorMessage = e.message ?? 'Unknown error';
      });
    }
  }

  Future<void> _stopPlaying() async {
    try {
      await platform.invokeMethod('stopPlaying');
      setState(() {
        _isPlaying = false;
        _errorMessage = '';
      });
    } on PlatformException catch (e) {
      _showError('Failed to stop playing: ${e.message}');
      setState(() {
        _isPlaying = false;
        _errorMessage = e.message ?? 'Unknown error';
      });
    }
  }

  void _showError(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message), backgroundColor: Colors.red),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Audio Recorder')),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            if (_errorMessage.isNotEmpty)
              Padding(
                padding: const EdgeInsets.all(8.0),
                child: Text(
                  _errorMessage,
                  style: const TextStyle(color: Colors.red),
                ),
              ),
            // Recording controls
            ElevatedButton(
              onPressed:
                  _isPlaying
                      ? null
                      : (_isRecording ? _stopRecording : _startRecording),
              child: Text(_isRecording ? 'Stop Recording' : 'Start Recording'),
            ),
            const SizedBox(height: 20),

            if (_hasRecording) ...[
              // Version selector
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Text('Original'),
                  Switch(
                    value: _playAutotuned,
                    onChanged:
                        _isPlaying
                            ? null
                            : (bool value) {
                              setState(() {
                                _playAutotuned = value;
                              });
                            },
                  ),
                  const Text('Auto-tuned'),
                ],
              ),
              const SizedBox(height: 20),
              // Playback controls
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  ElevatedButton(
                    onPressed: _isPlaying ? null : _startPlaying,
                    child: const Text('Play'),
                  ),
                  const SizedBox(width: 20),
                  ElevatedButton(
                    onPressed: _isPlaying ? _stopPlaying : null,
                    child: const Text('Stop'),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }

  @override
  void dispose() {
    if (_isPlaying) {
      _stopPlaying();
    }
    if (_isRecording) {
      _stopRecording();
    }
    super.dispose();
  }
}
