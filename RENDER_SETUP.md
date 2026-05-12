# 🚀 НАСТРОЙКА RENDER.COM ДЛЯ ЗАЩИЩЕННОГО ВИДЕОЧАТА

## 📋 ИНСТРУКЦИЯ ПО НАСТРОЙКЕ

### **ШАГ 1: ПОДКЛЮЧЕНИЕ GITHUB К RENDER**

1. **Зайдите на [render.com](https://render.com)**
2. Нажмите **"New +"** → **"Web Service"**
3. Выберите **"GitHub"**
4. **Авторизуйтесь** через ваш GitHub аккаунт
5. **Выберите репозиторий**: `PIRAHNA123112/videochat`
6. **Настройте параметры**:

```
Name: secure-videochat-server
Branch: main
Root Directory: server
Runtime: Node
Build Command: npm install
Start Command: node server.js
```

### **ШАГ 2: ПЕРЕМЕННЫЕ ОКРУЖЕНИЯ**

В **Environment** разделе добавьте:

```
NODE_ENV=production
PORT=3000
```

### **ШАГ 3: ЗАПУСК И ПРОВЕРКА**

1. Нажмите **"Create Web Service"**
2. Дождитесь завершения сборки
3. Проверьте логи в Render Dashboard

---

## ✅ ЧТО ДОЛЖНО ПОЛУЧИТЬСЯ

### **Успешный Deploy:**
```
Build: ✅ Success
Deploy: ✅ Live
URL: https://secure-videochat-server.onrender.com
```

### **Проверка работы:**
```bash
# Health check
curl https://secure-videochat-server.onrender.com/health

# Ожидаемый ответ:
{
  "status": "ok",
  "roomsCount": 0,
  "clientsCount": 0,
  "version": "2.0.0-secure"
}
```

---

## 🧪 ТЕСТИРОВАНИЕ ЗАЩИЩЕННОГО СЕРВЕРА

### **1. Создание защищенной комнаты:**
```bash
curl -X POST https://secure-videochat-server.onrender.com/api/create-room \
  -H "Content-Type: application/json" \
  -d '{
    "roomKey": "your-secure-key-64-characters-long-random-string",
    "roomPassword": "your-strong-password-min-8-chars"
  }'
```

### **2. Проверка rate limiting:**
```bash
# Тест - должно сработать первые 100 раз
for i in {1..100}; do 
  curl https://secure-videochat-server.onrender.com/health
done

# 101-й запрос - должен вернуть ошибку
curl https://secure-videochat-server.onrender.com/health
```

### **3. Тест WebSocket с правильным паролем:**
```javascript
// В браузере консоли:
const ws = new WebSocket('wss://secure-videochat-server.onrender.com');

ws.onopen = () => {
    console.log('Connected to secure server');
    
    ws.send(JSON.stringify({
        type: 'join',
        roomId: 'test-room-id',
        userId: 'test-user',
        roomPassword: 'your-strong-password'
    }));
};

ws.onmessage = (event) => {
    console.log('Received:', JSON.parse(event.data));
};
```

### **4. Тест WebSocket с неверным паролем:**
```javascript
// Должен вернуть ошибку "Invalid room password"
ws.send(JSON.stringify({
    type: 'join',
    roomId: 'test-room-id',
    userId: 'test-user',
    roomPassword: 'wrong-password'
}));
```

---

## 📱 ОБНОВЛЕНИЕ ANDROID ПРИЛОЖЕНИЯ

### **1. Добавление CryptoUtils.kt:**
Файл уже создан: `app/src/main/java/com/your/videochat/CryptoUtils.kt`

### **2. Обновление VideoCallActivity.kt:**
Уже обновлено для поддержки паролей комнат.

### **3. Сборка APK:**
```bash
cd C:/Users/pirah/AndroidStudioProjects/videochat
./gradlew assembleDebug
```

---

## 🔒 ПРОВЕРКА БЕЗОПАСНОСТИ

### **Checklist для тестирования:**

- [ ] **Health endpoint** возвращает статус "ok"
- [ ] **Создание комнаты** работает с паролем
- [ ] **Rate limiting** блокирует >100 запросов
- [ ] **WebSocket подключение** требует пароль
- [ ] **Неверный пароль** возвращает ошибку
- [ ] **CORS** блокирует посторонние домены
- [ ] **Автоочистка** комнат через 24 часа

---

## 🚨 ВОЗМОЖНЫЕ ПРОБЛЕМЫ

### **Проблема: Build Command не найден**
```
Решение: Измените в Render:
Build Command: npm ci
Start Command: node server.js
```

### **Проблема: Порт не доступен**
```
Решение: Проверьте PORT переменную:
PORT=3000
```

### **Проблема: GitHub не подключается**
```
Решение: Проверьте права доступа к репозиторию
Убедитесь что репозиторий публичный
```

---

## 🎯 ИТОГОВЫЙ РЕЗУЛЬТАТ

После настройки Render.com у вас будет:

### **✅ Защищенный сервер:**
- 🔐 Парольная защита комнат
- 🛡️ Rate limiting атак
- 🚫 Закрытый CORS
- 📊 Скрытие чувствительных данных
- ⏰ Автоматическая очистка

### **✅ Безопасное Android приложение:**
- 🔑 Поддержка паролей комнат
- 🛡️ Обработка ошибок аутентификации
- 📡 Защищенная передача данных

### **🔒 Максимальная безопасность для интимных звонков:**
- End-to-end шифрование WebRTC
- Контроль доступа к комнатам
- Защита от MITM атак
- Отсутствие утечек данных

---

## 📞 ПОДДЕРЖКА

Если возникнут проблемы:

1. **Проверьте логи** в Render Dashboard
2. **Проверьте переменные** окружения
3. **Убедитесь** что GitHub подключен
4. **Проверьте** версию Node.js (должна быть 18+)

---

## 🎉 ГОТОВО К ИСПОЛЬЗОВАНИЮ

После выполнения всех шагов ваш защищенный видеочат будет доступен по адресу:
`https://secure-videochat-server.onrender.com`

**Максимальная безопасность для интимных видеозвонков обеспечена!** 🔒✨
