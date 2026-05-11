# Подробная инструкция по развертыванию на Railway (для новичков)

## Что такое Railway?
Railway - это бесплатный сервис для хостинга приложений. Он автоматически берет ваш код с GitHub и запускает его в интернете.

## Шаг 1: Регистрация на GitHub (если нет аккаунта)

### 1.1. Зайдите на https://github.com
### 1.2. Нажмите зеленую кнопку "Sign up"
### 1.3. Заполните форму:
   - Username (имя пользователя): придумайте любое на английском
   - Email: ваша почта
   - Password: пароль
### 1.4. Нажмите "Create account"
### 1.5. Подтвердите email (проверьте почту и нажмите на ссылку)

## Шаг 2: Загрузка кода на GitHub

### 2.1. Создайте новый репозиторий
1. Зайдите на https://github.com
2. Нажмите кнопку "+" в правом верхнем углу
3. Выберите "New repository"
4. Заполните:
   - Repository name: `videochat` (или любое название)
   - Описание можно оставить пустым
   - Выберите "Public" (публичный)
5. Нажмите кнопку "Create repository"

### 2.2. Загрузите код через GitHub Desktop (проще для новичков)

#### Скачайте GitHub Desktop:
1. Зайдите на https://desktop.github.com/
2. Скачайте и установите программу
3. Запустите GitHub Desktop
4. Войдите в свой GitHub аккаунт

#### Загрузите код:
1. В GitHub Desktop нажмите "File" → "Add local repository"
2. Выберите папку `C:\Users\pirah\AndroidStudioProjects\videochat`
3. Нажмите "Add repository"
4. В поле "Current branch" напишите имя ветки: `main`
5. Нажмите кнопку "Publish repository"
6. В появившемся окне:
   - Убедитесь, что выбран ваш аккаунт
   - Repository name: `videochat`
   - Выберите "Keep this code private" или "Public" (лучше Public)
7. Нажмите "Publish repository"

**ИЛИ через командную строку (если умеете):**
```bash
cd C:\Users\pirah\AndroidStudioProjects\videochat
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/ВАШ_USERNAME/videochat.git
git push -u origin main
```

## Шаг 3: Регистрация на Railway

### 3.1. Зайдите на https://railway.app
### 3.2. Нажмите кнопку "Login" или "Sign Up"
### 3.3. Выберите "Continue with GitHub"
### 3.4. Разрешите Railway доступ к вашему GitHub
### 3.5. Railway предложит ввести кредитную карту (для бесплатного тарифа это НЕ обязательно, но могут попросить для верификации)

## Шаг 4: Создание проекта на Railway

### 4.1. На главной странице Railway нажмите "New Project"
### 4.2. Выберите "Deploy from GitHub repo"
### 4.3. Railway покажет список ваших репозиториев
### 4.4. Найдите и выберите репозиторий `videochat`
### 4.5. Если репозиторий не появился:
   - Нажмите "Configure GitHub app"
   - Найдите репозиторий `videochat`
   - Нажмите кнопку "Install" или "Connect"
   - Вернитесь и выберите репозиторий

## Шаг 5: Настройка проекта

### 5.1. После выбора репозитория Railway автоматически начнет анализ
### 5.2. Railway определит Node.js проект и покажет настройки

### Важно: Укажите правильную папку!
1. В поле "Root Directory" введите: `server`
2. Это важно, потому что сервер находится в папке `server`

### 5.3. Проверьте настройки:
   - **Root Directory**: `server`
   - **Build Command**: `npm install`
   - **Start Command**: `node server.js`
   - **Environment Variables**: можно пока оставить пустыми

### 5.4. Нажмите кнопку "Deploy"

## Шаг 6: Ожидание развертывания

### 6.1. Railway начнет развертывание
### 6.2. Вы увидите логи в реальном времени
### 6.3. Подождите 2-5 минут

### Что вы увидите в логах:
```
Building...
Installing dependencies...
Starting server...
Signalling сервер запущен на порту 3000
Ожидание подключений...
```

### 6.4. Когда развертывание завершится, вы увидите зеленую галочку

## Шаг 7: Получение URL сервера

### 7.1. После успешного развертывания Railway выдаст URL
### 7.2. URL будет выглядеть примерно так:
   ```
   https://videochat-production-1234.up.railway.app
   ```
   или
   ```
   https://videochat.up.railway.app
   ```

### 7.3. Скопируйте этот URL (нажмите на него, он скопируется)

## Шаг 8: Проверка сервера

### 8.1. Откройте браузер
### 8.2. Введите ваш URL + `/health`
   ```
   https://videochat-production-1234.up.railway.app/health
   ```
### 8.3. Вы должны увидеть:
   ```json
   {"status":"ok","rooms":[]}
   ```

Если видите это - сервер работает! 🎉

## Шаг 9: Обновление Android приложения

### 9.1. Откройте Android Studio
### 9.2. Откройте файл `MainActivity.kt`
   - Путь: `app/src/main/java/com/your/videochat/MainActivity.kt`
### 9.3. Найдите строку 44:
   ```kotlin
   private var SERVER_URL = "wss://your-app-name.onrender.com"
   ```
### 9.4. Замените на ваш Railway URL:
   ```kotlin
   private var SERVER_URL = "wss://videochat-production-1234.up.railway.app"
   ```
   **ВАЖНО:** Замените `https://` на `wss://` (добавьте букву w после http)

### 9.5. Откройте файл `VideoCallActivity.kt`
   - Путь: `app/src/main/java/com/your/videochat/VideoCallActivity.kt`
### 9.6. Найдите строку 48:
   ```kotlin
   private var SERVER_URL = "wss://your-app-name.onrender.com"
   ```
### 9.7. Замените на ваш Railway URL:
   ```kotlin
   private var SERVER_URL = "wss://videochat-production-1234.up.railway.app"
   ```

## Шаг 10: Добавление домена Railway в security config

### 10.1. Откройте файл `network_security_config.xml`
   - Путь: `app/src/main/res/xml/network_security_config.xml`
### 10.2. Убедитесь, что там есть строка:
   ```xml
   <domain includeSubdomains="true">railway.app</domain>
   ```
   (Она уже должна быть там из предыдущих изменений)

## Шаг 11: Сборка APK

### 11.1. В Android Studio нажмите в меню: Build → Generate Signed Bundle/APK
### 11.2. Выберите "APK" и нажмите "Next"
### 11.3. Создайте keystore (первый раз):
   - Нажмите "Create new..."
   - Key store path: выберите папку и назовите файл `keystore.jks`
   - Password: придумайте пароль (запомните его!)
   - Key alias: `videochat`
   - Key password: такой же как keystore password
   - First and Last Name: ваше имя
   - Organization: можно оставить пустым
   - Country: RU
   - Нажмите "OK"

### 11.4. Выберите созданный keystore и нажмите "Next"
### 11.5. Выберите "release" и нажмите "Finish"
### 11.6. Подождите сборки (1-2 минуты)
### 11.7. APK будет в папке: `app/release/app-release.apk`

## Шаг 12: Установка и тестирование

### 12.1. Перенесите APK на телефон
   - Через USB кабель
   - Или отправьте через Telegram/WhatsApp
   - Или загрузите на Google Drive

### 12.2. На телефоне:
   - Разрешите установку из неизвестных источников в настройках
   - Откройте APK файл
   - Установите приложение

### 12.3. Тестирование:
   1. Установите приложение на ДВА разных телефона
   2. Откройте приложение на обоих
   3. Вы должны увидеть статус "Подключено" (зеленый)
   4. На первом телефоне введите название комнаты, например "test1"
   5. Нажмите "Создать комнату"
   6. На втором телефоне введите то же название "test1"
   7. Нажмите "Присоединиться"
   8. Должен начаться видеозвонок!

## Возможные проблемы и решения

### Проблема: Railway не находит репозиторий
**Решение:**
1. Нажмите "Configure GitHub app" на Railway
2. Найдите ваш репозиторий `videochat`
3. Нажмите "Install" или "Connect"
4. Вернитесь и выберите репозиторий снова

### Проблема: Ошибка при развертывании
**Решение:**
1. Проверьте, что Root Directory = `server`
2. Проверьте, что в папке `server` есть файлы:
   - `package.json`
   - `server.js`
3. Проверьте логи развертывания (кнопка "View logs")

### Проблема: Сервер развернулся, но приложение не подключается
**Решение:**
1. Проверьте, что используете `wss://` а не `https://`
2. Проверьте, что URL скопирован правильно
3. Проверьте network_security_config.xml
4. Посмотрите логи в Android Studio (Logcat)

### Проблема: Нет звука или видео
**Решение:**
1. Проверьте разрешения приложения (Камера, Микрофон)
2. Проверьте, что STUN серверы настроены (уже настроены в коде)
3. Попробуйте подключиться через другой интернет

## Бесплатные ограничения Railway

- **512 MB RAM** - достаточно для signaling сервера
- **1 GB диска** - достаточно
- **$5 бесплатных кредитов в месяц** - хватит для небольшого проекта
- **Сон после 15 минут бездействия** - сервер может "уснуть"
  - При первом подключении проснется за 10-30 секунд

## Как предотвратить "сон" сервера (опционально)

Railway имеет бесплатный план с ограничениями. Чтобы сервер не засыпал:

1. Установите на сервер пинг-сервис (например, UptimeRobot)
2. Или регулярно подключайтесь к серверу

## Где смотреть логи сервера

1. Зайдите на https://railway.app
2. Откройте ваш проект
3. Нажмите на сервис (сервер)
4. Нажмите "View logs"
5. Там вы увидите все подключения и ошибки

## Удаление или переразвертывание

### Если нужно обновить код:
1. Измените код локально
2. Загрузите изменения на GitHub (GitHub Desktop → Commit → Push)
3. Railway автоматически переразвернет

### Если нужно удалить:
1. На Railway откройте проект
2. Нажмите "Settings"
3. Прокрутите вниз
4. Нажмите "Delete Project"

## Контакты и помощь

Если что-то не работает:
1. Проверьте логи на Railway
2. Проверьте логи в Android Studio (Logcat)
3. Убедитесь, что интернет работает
4. Протестируйте сервер: `https://ваш-url.railway.app/health`

## Краткая шпаргалка

1. **GitHub** → Создать репозиторий → Загрузить код
2. **Railway** → New Project → Deploy from GitHub → Выбрать репозиторий
3. **Настройка** → Root Directory: `server` → Deploy
4. **Получить URL** → Скопировать
5. **Android Studio** → Заменить URL в MainActivity.kt и VideoCallActivity.kt
6. **Собрать APK** → Установить на телефоны → Тестировать

Удачи! 🚀
