# Развертывание VideoChat для работы в России

## Обзор
Это руководство поможет развернуть signaling сервер для работы приложения по всей России, а не только локально.

## Шаг 1: Развертывание сервера

### Вариант A: Render (Рекомендуется - бесплатно)

1. Создайте аккаунт на https://render.com
2. Подключите GitHub репозиторий с проектом
3. Создайте новый Web Service:
   - Нажмите "New +" → "Web Service"
   - Подключите репозиторий
   - Root Directory: `server`
   - Build Command: `npm install`
   - Start Command: `node server.js`
   - Instance Type: Free
4. После развертывания вы получите URL вида: `https://your-app-name.onrender.com`

### Вариант B: Railway (Бесплатно)

1. Создайте аккаунт на https://railway.app
2. Нажмите "New Project" → "Deploy from GitHub repo"
3. Выберите репозиторий
4. Railway автоматически определит Node.js проект
5. После развертывания вы получите URL вида: `https://your-app-name.railway.app`

### Вариант C: VPS (Timeweb, Beget, Selectel - для России)

Для работы внутри России лучше использовать российские VPS провайдеры:

1. **Timeweb** (https://timeweb.com)
2. **Beget** (https://beget.com)
3. **Selectel** (https://selectel.ru)

Инструкция для VPS:
```bash
# Установите Node.js (Ubuntu/Debian)
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt-get install -y nodejs

# Скопируйте файлы сервера
scp -r server/* user@your-server-ip:/opt/videochat/

# Подключитесь к серверу
ssh user@your-server-ip
cd /opt/videochat

# Установите зависимости
npm install

# Установите PM2 для управления процессом
sudo npm install -g pm2

# Запустите сервер
pm2 start server.js --name videochat
pm2 save
pm2 startup

# Настройте Nginx (опционально, для SSL)
sudo apt install nginx certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.com
```

## Шаг 2: Обновление Android приложения

### Обновите URL сервера в MainActivity.kt

Замените `your-app-name.onrender.com` на ваш реальный URL:

```kotlin
companion object {
    private const val TAG = "MainActivity"
    private var SERVER_URL = "wss://your-actual-app-name.onrender.com"
}
```

### Обновите URL сервера в VideoCallActivity.kt

```kotlin
companion object {
    private const val TAG = "VideoCall"
    private var SERVER_URL = "wss://your-actual-app-name.onrender.com"
}
```

### Обновите network_security_config.xml (если используете свой домен)

Если вы используете свой домен с VPS, добавьте его:

```xml
<domain includeSubdomains="true">your-domain.com</domain>
```

## Шаг 3: Сборка и установка APK

1. Откройте проект в Android Studio
2. Выберите Build → Generate Signed Bundle/APK
3. Выберите APK
4. Создайте новый keystore или используйте существующий
5. Соберите release APK
6. Установите на устройства или загрузите в Google Play

## Шаг 4: Тестирование

### Тестирование соединения
1. Установите приложение на два разных устройства
2. Убедитесь, что устройства имеют доступ к интернету
3. Подключитесь к серверу
4. Создайте комнату на одном устройстве
5. Присоединитесь к той же комнате на другом устройстве
6. Проверьте видео и аудио

### Проверка NAT traversal
STUN серверы уже настроены в приложении для работы через NAT и фаерволы. Если соединение не устанавливается:

1. Проверьте, что сервер доступен: `curl https://your-app-name.onrender.com/health`
2. Проверьте логи сервера
3. Убедитесь, что устройства не блокируют WebSocket соединения

## Шаг 5: Оптимизация для России

### TURN серверы (опционально)
Для пользователей за строгими фаерволами или симметричным NAT может потребоваться TURN сервер.

Рекомендуемые TURN провайдеры:
- Twilio (платно, но надежно)
- coturn (самостоятельный部署 на VPS)

Пример добавления TURN сервера в VideoCallActivity.kt:
```kotlin
private val iceServers = listOf(
    PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
    PeerConnection.IceServer.builder("turn:your-turn-server.com:3478")
        .setUsername("username")
        .setPassword("password")
        .createIceServer()
)
```

### Российские серверы
Для лучшей производительности в России:
- Используйте VPS в России (Москва, Санкт-Петербург)
- Рассмотрите CDN для статических ресурсов
- Настройте мониторинг (UptimeRobot, Pingdom)

## Troubleshooting

### Проблема: Не удается подключиться к серверу
- Проверьте, что сервер запущен: `pm2 status` или проверьте логи в Render/Railway
- Убедитесь, что используете правильный протокол (wss:// для HTTPS, ws:// для HTTP)
- Проверьте network_security_config.xml

### Проблема: Видео не работает
- Проверьте разрешения камеры и микрофона
- Убедитесь, что STUN серверы доступны
- Проверьте логи в Logcat

### Проблема: Плохое качество видео
- Проверьте скорость интернета
- Уменьшите разрешение в VideoCallActivity.kt (строка 186):
  ```kotlin
  capturer.startCapture(640, 480, 30)  // Вместо 1280x720
  ```

## Безопасность

1. **Используйте WSS (WebSocket Secure)** - уже настроено
2. **Добавьте аутентификацию** - для производства рекомендуется добавить токен аутентификацию
3. **Ограничьте CORS** - сейчас разрешены все источники, для производства укажите конкретные домены
4. **Rate limiting** - добавьте ограничение количества запросов для защиты от DDoS

## Мониторинг

- Render/Railway предоставляют встроенный мониторинг
- Для VPS используйте: PM2 monitoring, Grafana, Prometheus
- Логируйте важные события для отладки

## Поддержка

При возникновении проблем:
1. Проверьте логи сервера
2. Проверьте Logcat в Android Studio
3. Убедитесь, что сервер отвечает на /health endpoint
4. Протестируйте WebSocket соединение с помощью онлайн инструментов
