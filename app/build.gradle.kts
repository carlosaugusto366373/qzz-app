        function connectAdb() {
            const ip = document.getElementById('adbIp').value;
            const port = document.getElementById('adbCode').value;

            if (!ip || !port) {
                showToast('Preencha o IP e a Porta (5555)', 'error');
                return;
            }

            showToast('Conectando...', '');
            if (typeof QZZAdb !== 'undefined') {
                QZZAdb.pairAdb(ip, "", port);
            } else {
                showToast('ADB não suportado nesse dispositivo', 'error');
            }
        }