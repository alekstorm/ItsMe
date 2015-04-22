(function(window) {
  var TARGET_SAMPLE_RATE = 16000;

  var onFail = function(e) {
    console.warn('Rejected!', e);
  };

  var onSuccess = function(s, url, strategy) {
    var source = (new webkitAudioContext()).createMediaStreamSource(s);
    var node = (source.context.createScriptProcessor ||
                source.context.createJavaScriptNode).call(source.context, 4096, 1, 1);

    var socket = new WebSocket('wss://itsme:5000' + url, []);
    var total = null;

    if (strategy == 'challenge')
      $('#progress-outer')[0].style.visibility = 'visible';

    socket.onopen = function() {
      node.onaudioprocess = function(ev) {
        var samples = ev.inputBuffer.getChannelData(0);

        var ratio = TARGET_SAMPLE_RATE / ev.inputBuffer.sampleRate;

        var newLength = Math.floor(samples.length * ratio);
        var resampled = new DataView(new ArrayBuffer(newLength * 4));

        // TODO combine for-loops
        // TODO downsampling adds buzzing - aliasing effect?
        for (var i = 0; i < newLength; i++) {
          var j = i / ratio;
          // TODO real triangle interpolation, instead of simple average (delay?)
          // - actually, FIR decimation
          resampled.setFloat32(i * 4, (samples[Math.floor(j)] + samples[Math.ceil(j)]) / 2);
        }

        // TODO cull frequently-unrecognized words from dictionary
        var pcm = new DataView(new ArrayBuffer(newLength * 2));

        // TODO do in separate thread
        for (var i = 0; i < resampled.buffer.byteLength / 4; i++) {
          var s = Math.max(-1, Math.min(1, resampled.getFloat32(i * 4)));
          pcm.setInt16(i * 2, s < 0 ? s * 0x8000 : s * 0x7FFF, true);
        }

        socket.send(pcm.buffer);
      };
    };

    socket.onmessage = function(ev) {
      var message = JSON.parse(ev.data);
      switch (strategy) {
        case 'challenge':
          switch (message.name) {
            case 'next':
              if (total == null)
                total = message.params.remaining;
              $('#word')[0].innerHTML = message.params.word;
              var percentage = Math.round((total - message.params.remaining) / (total + 1) * 100) + '%';
              //$('#progress-inner')[0].innerHTML = percentage;
              $('#progress-inner')[0].style.width = percentage;
              $('#word-container')[0].style.height = '130px';
              break;
            case 'recognizing':
              $('#progress-inner')[0].style.width = '100%';
              $('#word')[0].style.display = 'none';
              $('#loading')[0].style.display = 'block';
              node.onaudioprocess = null; // TODO stop recording altogether, don't just remove callback, disconnect socket
              break;
            case 'result':
              socket.close();
              $('#form-container')[0].style.display = 'none';
              if (message.params.success)
                $('#success')[0].style.display = 'block';
              else
                $('#failure')[0].style.display = 'block';
              break;
          }
          break;
        case 'pin':
          switch (message.name) {
            case 'start':
              $('#word')[0].innerHTML = message.params.code.join('-');
              $('#word-container')[0].style.height = '70px';
              $('#progress-outer')[0].style.display = 'none';
              break;
            case 'success':
              node.onaudioprocess = null;
              socket.close();
              $('#form-container')[0].style.display = 'none';
              $('#success')[0].style.display = 'block';
              break;
          }
          break;
      }
    }

    source.connect(node);
    node.connect(source.context.destination); // this shouldn't be necessary
  }

  navigator.getUserMedia = navigator.getUserMedia || navigator.webkitGetUserMedia || navigator.mozGetUserMedia || navigator.msGetUserMedia;

  window.record = function(url, strategy) {
    if (navigator.getUserMedia)
      navigator.getUserMedia({audio: true}, function(s) { onSuccess(s, url, strategy); }, onFail);
    else
      console.error('navigator.getUserMedia not present');
  }
})(window);
