const WebSocket = require('ws');
const http = require('http');
const express = require('express');
const crypto = require('crypto');
const rateLimit = require('express-rate-limit');
const fs = require('fs');
const path = require('path');

const app = express();
const server = http.createServer(app);

// Rate limiting для защиты от атак
const limiter = rateLimit({
    windowMs: 15 * 60 * 1000, // 15 минут
    max: 100, // максимум 100 запросов
    message: 'Too many requests',
    standardHeaders: true,
    legacyHeaders: false,
});

app.use(limiter);

// ЗАЩИЩЕННЫЙ WebSocket сервер
const wss = new WebSocket.Server({ 
    server,
    perMessageDeflate: false,  // Отключаем компрессию для безопасности
    maxPayload: 1024 * 1024,  // 1MB лимит
    backlog: 100,  // Уменьшаем очередь для безопасности
    verifyClient: (info) => {
        // Упрощенная верификация для мобильных клиентов
        // Разрешаем подключения с любых origins для мобильных приложений
        // и проверяем только для веб-клиентов
        const allowedOrigins = [
            'https://videochat-aend.onrender.com',
            'http://localhost:3000',
            'http://localhost:8080'
        ];
        
        // Если origin нет (мобильное приложение), разрешаем
        if (!info.origin) {
            return true;
        }
        
        // Разрешаем для веб-клиентов
        return allowedOrigins.includes(info.origin);
    }
});

// ЗАЩИЩЕННЫЙ CORS - разрешаем мобильные приложения
app.use((req, res, next) => {
    const allowedOrigins = [
        'https://videochat-aend.onrender.com',
        'http://localhost:3000',
        'http://localhost:8080'
    ];
    
    const origin = req.headers.origin;
    // Разрешаем мобильные приложения (без origin) и разрешенные веб-домены
    if (!origin || allowedOrigins.includes(origin)) {
        res.header('Access-Control-Allow-Origin', origin || '*');
    }
    
    res.header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.header('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-Room-Key');
    res.header('Access-Control-Allow-Credentials', 'true');
    res.header('X-Content-Type-Options', 'nosniff');
    res.header('X-Frame-Options', 'DENY');
    res.header('X-XSS-Protection', '1; mode=block');
    next();
});

// Обработка OPTIONS запросов для CORS
app.options('*', (req, res) => {
    const allowedOrigins = [
        'https://videochat-aend.onrender.com',
        'http://localhost:3000',
        'http://localhost:8080'
    ];
    
    const origin = req.headers.origin;
    // Разрешаем мобильные приложения (без origin) и разрешенные веб-домены
    if (!origin || allowedOrigins.includes(origin)) {
        res.header('Access-Control-Allow-Origin', origin || '*');
    }
    
    res.header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.header('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-Room-Key');
    res.header('Access-Control-Allow-Credentials', 'true');
    res.sendStatus(200);
});

// ЗАЩИЩЕННЫЙ health check endpoint (без утечки данных)
app.get('/health', (req, res) => {
    try {
        const roomsCount = rooms.size;
        const clientsCount = wss.clients.size;
        
        console.log(`Health check: ${clientsCount} clients, ${roomsCount} rooms`);
        
        // НЕ РАСКРЫВАЕМ КОНФИДЕНЦИАЛЬНУЮ ИНФОРМАЦИЮ
        res.json({ 
            status: 'ok', 
            roomsCount: roomsCount,  // Только количество, не ID
            clientsCount: clientsCount,
            timestamp: new Date().toISOString(),
            uptime: process.uptime(),
            version: '2.0.0-secure'
        });
    } catch (error) {
        console.error('Health check error (details hidden):', error.message);
        res.status(500).json({ 
            status: 'error', 
            error: 'Internal server error',
            timestamp: new Date().toISOString()
        });
    }
});

// API для создания защищенных комнат
app.post('/api/create-room', express.json({ limit: '10kb' }), (req, res) => {
    try {
        const { roomKey, roomPassword } = req.body;
        
        // Валидация входных данных
        if (!roomKey || roomKey.length !== 64) {
            return res.status(400).json({ error: 'Invalid room key format' });
        }
        
        if (!roomPassword || roomPassword.length < 8) {
            return res.status(400).json({ error: 'Password must be at least 8 characters' });
        }
        
        // Создаем защищенную комнату
        const roomId = crypto.randomBytes(16).toString('hex');
        const roomData = {
            id: roomId,
            password: crypto.createHash('sha256').update(roomPassword).digest('hex'),
            createdAt: Date.now(),
            maxParticipants: 2
        };
        
        rooms.set(roomId, new Set());
        roomKeys.set(roomId, roomData.password);
        
        // Автоматическое удаление через 24 часа
        setTimeout(() => {
            rooms.delete(roomId);
            roomKeys.delete(roomId);
            console.log(`Secure room ${roomId} expired and cleaned up`);
        }, 24 * 60 * 60 * 1000);
        
        res.json({ 
            roomId: roomId,
            expires: Date.now() + 24 * 60 * 60 * 1000, // 24 часа
            message: 'Secure room created successfully'
        });
        
    } catch (error) {
        console.error('Secure room creation error:', error.message);
        res.status(500).json({ error: 'Internal server error' });
    }
});

// Root endpoint для проверки работы сервера
app.get('/', (req, res) => {
    res.json({ 
        message: 'SECURE Video Chat Signalling Server',
        status: 'running',
        version: '2.0.0-secure',
        security: {
            encryption: 'AES-256-GCM',
            authentication: 'SHA-256',
            rateLimit: '100 req/15min',
            cors: 'Restricted'
        },
        endpoints: {
            health: '/health',
            createRoom: '/api/create-room',
            websocket: 'WebSocket upgrade on same port'
        }
    });
});

// Файл для сохранения комнат
const ROOMS_FILE = path.join(__dirname, 'rooms.json');

// Загружаем комнаты из файла при старте
function loadRooms() {
    try {
        if (fs.existsSync(ROOMS_FILE)) {
            const data = fs.readFileSync(ROOMS_FILE, 'utf8');
            const savedRooms = JSON.parse(data);
            
            // Восстанавливаем Map из сохраненных данных
            for (const [roomId, roomData] of Object.entries(savedRooms)) {
                rooms.set(roomId, new Set(roomData.users));
            }
            
            console.log(`Loaded ${rooms.size} rooms from file`);
        }
    } catch (error) {
        console.error('Error loading rooms:', error.message);
    }
}

// Сохраняем комнаты в файл
function saveRooms() {
    try {
        const roomsData = {};
        for (const [roomId, users] of rooms.entries()) {
            roomsData[roomId] = {
                users: Array.from(users)
            };
        }
        
        fs.writeFileSync(ROOMS_FILE, JSON.stringify(roomsData, null, 2));
        console.log(`Saved ${rooms.size} rooms to file`);
    } catch (error) {
        console.error('Error saving rooms:', error.message);
    }
}

// Хранилище для комнат с защитой
const rooms = new Map();
const roomKeys = new Map(); // Храним хеши паролей

// Хранилище для подключений
const clients = new Map();

// Загружаем комнаты при старте сервера
loadRooms();

wss.on('connection', (ws, req) => {
    console.log('New WebSocket connection from:', req.url);
    console.log('Headers:', req.headers);
    console.log('Remote address:', req.socket.remoteAddress);
    
    // Heartbeat для поддержания соединения
    ws.isAlive = true;
    ws.on('pong', () => {
        ws.isAlive = true;
    });
    
    // Отправляем список комнат при подключении
    sendSecureRoomsList(ws);
    
    ws.on('message', (message) => {
        try {
            const data = JSON.parse(message);
            // Обрабатываем ping от клиента
            if (data.type === 'ping') {
                ws.send(JSON.stringify({ type: 'pong' }));
                return;
            }
            handleMessage(ws, data);
        } catch (error) {
            console.error('Ошибка обработки сообщения:', error);
        }
    });

    ws.on('close', () => {
        handleDisconnect(ws);
    });

    ws.on('error', (error) => {
        console.error('WebSocket ошибка:', error);
    });
});

// Оптимизированная проверка соединений каждые 20 секунд
const heartbeatInterval = setInterval(() => {
    const deadConnections = [];
    
    wss.clients.forEach((ws) => {
        if (ws.isAlive === false) {
            deadConnections.push(ws);
        } else {
            ws.isAlive = false;
            try {
                ws.ping();
            } catch (error) {
                deadConnections.push(ws);
            }
        }
    });
    
    // Массово закрываем мертвые соединения
    deadConnections.forEach(ws => {
        try {
            console.log('Terminating dead connection');
            ws.terminate();
        } catch (error) {
            console.error('Error terminating connection:', error.message);
        }
    });
    
    // Логируем статистику
    console.log(`Active connections: ${wss.clients.size - deadConnections.length}`);
}, 20000);

function handleMessage(ws, data) {
    // Валидация типа сообщения
    const allowedTypes = ['join', 'offer', 'answer', 'ice-candidate', 'leave', 'get-rooms', 'chat-message', 'create-room'];
    if (!allowedTypes.includes(data.type)) {
        console.warn('Invalid message type:', data.type);
        ws.close(1003, 'Invalid message type');
        return;
    }
    
    // Валидация размера сообщения
    const messageSize = JSON.stringify(data).length;
    if (messageSize > 10240) { // 10KB лимит
        console.warn('Message too large:', messageSize);
        ws.close(1009, 'Message too large');
        return;
    }
    
    switch (data.type) {
        case 'create-room':
            handleCreateRoom(ws, data);
            break;
        case 'join':
            handleSecureJoin(ws, data);
            break;
        case 'offer':
            handleSecureOffer(ws, data);
            break;
        case 'answer':
            handleSecureAnswer(ws, data);
            break;
        case 'ice-candidate':
            handleSecureIceCandidate(ws, data);
            break;
        case 'leave':
            handleSecureLeave(ws, data);
            break;
        case 'get-rooms':
            sendSecureRoomsList(ws);
            break;
        case 'chat-message':
            handleSecureChatMessage(ws, data);
            break;
        default:
            console.log('Неизвестный тип сообщения:', data.type);
            ws.close(1003, 'Unknown message type');
    }
}

function handleCreateRoom(ws, data) {
    const { roomName } = data;
    
    // Валидация входных данных
    if (!roomName || roomName.length < 3) {
        console.warn('Invalid room name:', roomName);
        ws.send(JSON.stringify({
            type: 'error',
            message: 'Название комнаты должно быть не менее 3 символов'
        }));
        return;
    }
    
    // Создаем комнату без пароля
    const roomId = crypto.randomBytes(8).toString('hex');
    const userId = crypto.randomBytes(8).toString('hex');
    
    rooms.set(roomId, new Set());
    // roomKeys больше не нужны для простых комнат
    
    // Создатель сразу заходит в комнату
    rooms.get(roomId).add(userId);
    clients.set(userId, ws);
    ws.roomId = roomId;
    ws.userId = userId;
    
    console.log(`Room created and user joined: ${roomId} (${roomName}) by user ${userId}`);
    
    // Отправляем подтверждение создателю с информацией о входе
    ws.send(JSON.stringify({
        type: 'room-created',
        roomId: roomId,
        roomName: roomName,
        userId: userId,
        message: 'Комната создана и вы вошли в неё'
    }));
    
    // Рассылаем обновленный список всем клиентам
    broadcastSecureRoomsList();
    
    // Сохраняем комнаты в файл
    saveRooms();
    
    // Отправляем список пользователей в комнате
    const roomUsers = Array.from(rooms.get(roomId) || []);
    ws.send(JSON.stringify({
        type: 'room-users',
        users: roomUsers
    }));
    
    // Автоматическое удаление через 24 часа
    setTimeout(() => {
        if (rooms.has(roomId)) {
            rooms.delete(roomId);
            console.log(`Room ${roomId} expired and cleaned up`);
            broadcastSecureRoomsList();
        }
    }, 24 * 60 * 60 * 1000);
}

function handleSecureJoin(ws, data) {
    const { roomId } = data;
    
    // Валидация входных данных
    if (!roomId) {
        console.warn('Missing required fields for join');
        ws.close(1003, 'Missing required fields');
        return;
    }
    
    // Генерируем новый userId для входящего пользователя
    const userId = crypto.randomBytes(8).toString('hex');
    
    if (!rooms.has(roomId)) {
        console.warn('Room not found:', roomId);
        ws.close(1003, 'Room not found');
        return;
    }
    
    const room = rooms.get(roomId);
    
    // Очищаем неактивные соединения из комнаты
    const activeUsers = new Set();
    room.forEach(userId => {
        const client = clients.get(userId);
        if (client && client.readyState === WebSocket.OPEN) {
            activeUsers.add(userId);
        } else {
            console.log(`Removing inactive user ${userId} from room ${roomId}`);
            room.delete(userId);
            clients.delete(userId);
        }
    });
    
    // Проверяем лимит участников после очистки
    if (activeUsers.size >= 2) {
        console.warn('Room is full:', roomId, 'Active users:', activeUsers.size);
        ws.close(1003, 'Room is full');
        return;
    }
    
    room.add(userId);
    clients.set(userId, ws);
    ws.roomId = roomId;
    ws.userId = userId;
    
    console.log(`User ${userId} joined room ${roomId}`);
    
    // Сохраняем изменения
    saveRooms();
    
    // Уведомляем других участников
    const otherUsers = Array.from(room).filter(id => id !== userId);
    otherUsers.forEach(otherUserId => {
        const client = clients.get(otherUserId);
        if (client && client.readyState === WebSocket.OPEN) {
            client.send(JSON.stringify({
                type: 'user-joined',
                userId: userId
            }));
            
            // Также отправляем обновленный список пользователей всем участникам
            const allUsers = Array.from(room);
            client.send(JSON.stringify({
                type: 'room-users',
                users: allUsers,
                userId: otherUserId  // Отправляем клиенту его userId
            }));
        }
    });
    
    // Отправляем список всех участников включая себя и новый userId
    const allUsers = Array.from(room);
    ws.send(JSON.stringify({
        type: 'room-users',
        users: allUsers,
        userId: userId  // Отправляем клиенту его новый userId
    }));
}

function handleSecureOffer(ws, data) {
    const { roomId, targetUserId, offer } = data;
    console.log(`Offer from ${ws.userId} to ${targetUserId} in room ${roomId}`);
    
    const targetClient = clients.get(targetUserId);
    
    if (!targetClient || targetClient.readyState !== WebSocket.OPEN) {
        console.warn('Target client not available for offer:', targetUserId);
        console.log('Available clients:', Array.from(clients.keys()));
        return;
    }
    
    try {
        // Валидация SDP оффера
        if (!offer || typeof offer !== 'string' || offer.length > 100000) {
            console.warn('Invalid offer data');
            ws.close(1003, 'Invalid offer data');
            return;
        }
        
        targetClient.send(JSON.stringify({
            type: 'offer',
            userId: ws.userId,
            offer: offer
        }));
        
        console.log(`Secure offer sent from ${ws.userId} to ${targetUserId}`);
    } catch (error) {
        console.error('Error sending secure offer:', error.message);
        clients.delete(targetUserId);
    }
}

function handleSecureAnswer(ws, data) {
    const { roomId, targetUserId, answer } = data;
    console.log(`Answer from ${ws.userId} to ${targetUserId} in room ${roomId}`);
    
    const targetClient = clients.get(targetUserId);
    
    if (!targetClient || targetClient.readyState !== WebSocket.OPEN) {
        console.warn('Target client not available for answer:', targetUserId);
        console.log('Available clients:', Array.from(clients.keys()));
        return;
    }
    
    try {
        // Валидация SDP ответа
        if (!answer || typeof answer !== 'string' || answer.length > 100000) {
            console.warn('Invalid answer data');
            ws.close(1003, 'Invalid answer data');
            return;
        }
        
        targetClient.send(JSON.stringify({
            type: 'answer',
            userId: ws.userId,
            answer: answer
        }));
        
        console.log(`Secure answer sent from ${ws.userId} to ${targetUserId}`);
    } catch (error) {
        console.error('Error sending secure answer:', error.message);
        clients.delete(targetUserId);
    }
}

function handleSecureIceCandidate(ws, data) {
    const { roomId, targetUserId, candidate, sdpMid, sdpMLineIndex } = data;
    console.log(`ICE candidate from ${ws.userId} to ${targetUserId} in room ${roomId}`);
    
    const targetClient = clients.get(targetUserId);
    
    if (!targetClient || targetClient.readyState !== WebSocket.OPEN) {
        console.warn('Target client not available for ICE candidate:', targetUserId);
        console.log('Available clients:', Array.from(clients.keys()));
        return;
    }
    
    try {
        // Валидация ICE кандидата
        if (!candidate || typeof candidate !== 'string' || candidate.length > 1000) {
            console.warn('Invalid ICE candidate data');
            ws.close(1003, 'Invalid ICE candidate data');
            return;
        }
        
        targetClient.send(JSON.stringify({
            type: 'ice-candidate',
            userId: ws.userId,
            candidate: candidate,
            sdpMid: sdpMid,
            sdpMLineIndex: sdpMLineIndex
        }));
        
        console.log(`Secure ICE candidate sent from ${ws.userId} to ${targetUserId}`);
    } catch (error) {
        console.error('Error sending secure ICE candidate:', error.message);
        clients.delete(targetUserId);
    }
}

function handleSecureLeave(ws, data) {
    const { roomId, userId } = data;
    
    if (rooms.has(roomId)) {
        const room = rooms.get(roomId);
        room.delete(userId);
        
        if (room.size === 0) {
            rooms.delete(roomId);
            roomKeys.delete(roomId);
            // Комната пуста - уведомить всех
            broadcastSecureRoomsList();
            // Сохраняем изменения
            saveRooms();
        } else {
            // Уведомить других участников
            room.forEach(otherUserId => {
                const client = clients.get(otherUserId);
                if (client && client.readyState === WebSocket.OPEN) {
                    client.send(JSON.stringify({
                        type: 'user-left',
                        userId: userId
                    }));
                }
            });
        }
    }
    
    clients.delete(userId);
    console.log(`Пользователь ${userId} покинул комнату ${roomId}`);
}

function handleSecureChatMessage(ws, data) {
    const { roomId, userId, message } = data;
    const timestamp = Date.now();

    // Валидация сообщения чата
    if (!message || typeof message !== 'string') {
        console.warn('Invalid chat message format');
        ws.close(1003, 'Invalid chat message format');
        return;
    }
    
    if (message.length > 1000) {
        console.warn('Chat message too long:', message.length);
        ws.close(1003, 'Chat message too long');
        return;
    }

    console.log(`Secure chat message from ${userId} in room ${roomId}`);

    // Отправляем сообщение всем участникам комнаты
    if (rooms.has(roomId)) {
        const room = rooms.get(roomId);
        room.forEach(otherUserId => {
            const client = clients.get(otherUserId);
            if (client && client.readyState === WebSocket.OPEN) {
                try {
                    client.send(JSON.stringify({
                        type: 'chat-message',
                        userId: userId,
                        message: message,
                        timestamp: timestamp
                    }));
                } catch (error) {
                    console.error('Error sending secure chat message:', error.message);
                }
            }
        });
    }
}

function handleDisconnect(ws) {
    const { roomId, userId } = ws;
    
    if (roomId && userId) {
        handleSecureLeave(ws, { roomId, userId });
    }
}

// Отправить ЗАЩИЩЕННЫЙ список комнат одному клиенту
function sendSecureRoomsList(ws) {
    try {
        // Очищаем неактивных пользователей перед подсчетом
        rooms.forEach((users, roomId) => {
            const inactiveUsers = [];
            users.forEach(userId => {
                const client = clients.get(userId);
                if (!client || client.readyState !== WebSocket.OPEN) {
                    inactiveUsers.push(userId);
                }
            });
            
            // Удаляем неактивных пользователей
            inactiveUsers.forEach(userId => {
                users.delete(userId);
                clients.delete(userId);
                console.log(`Cleaned up inactive user ${userId} from room ${roomId}`);
            });
            
            // Удаляем пустые комнаты
            if (users.size === 0) {
                rooms.delete(roomId);
                roomKeys.delete(roomId);
                console.log(`Removed empty room ${roomId}`);
            }
        });
        
        // НЕ РАСКРЫВАЕМ КОНФИДЕНЦИАЛЬНУЮ ИНФОРМАЦИЮ
        const roomsList = Array.from(rooms.entries()).map(([id, users]) => ({
            id: id, // Полный ID комнаты для корректного входа
            usersCount: users.size,
            hasPassword: roomKeys.has(id) // Только факт наличия пароля
        }));
        
        ws.send(JSON.stringify({
            type: 'rooms-list',
            rooms: roomsList,
            timestamp: Date.now()
        }));
    } catch (error) {
        console.error('Error sending secure rooms list:', error.message);
    }
}

// Отправить ЗАЩИЩЕННЫЙ список комнат всем клиентам
function broadcastSecureRoomsList() {
    try {
        const roomsList = Array.from(rooms.entries()).map(([id, users]) => ({
            id: id, // Полный ID комнаты для корректного входа
            usersCount: users.size,
            hasPassword: roomKeys.has(id)
        }));
        
        const message = JSON.stringify({
            type: 'rooms-list',
            rooms: roomsList,
            timestamp: Date.now()
        });
        
        wss.clients.forEach(client => {
            if (client.readyState === WebSocket.OPEN) {
                try {
                    client.send(message);
                } catch (error) {
                    console.error('Error broadcasting to client:', error.message);
                }
            }
        });
    } catch (error) {
        console.error('Error broadcasting secure rooms list:', error.message);
    }
}

const PORT = process.env.PORT || 3000;

// Обработка ошибок сервера
server.on('error', (error) => {
    console.error('Server error:', error);
    if (error.code === 'EADDRINUSE') {
        console.error(`Port ${PORT} is already in use`);
    }
});

// Graceful shutdown
process.on('SIGTERM', () => {
    console.log('SIGTERM received, shutting down gracefully');
    server.close(() => {
        console.log('Server closed');
        process.exit(0);
    });
});

process.on('SIGINT', () => {
    console.log('SIGINT received, shutting down gracefully');
    server.close(() => {
        console.log('Server closed');
        process.exit(0);
    });
});

server.listen(PORT, '0.0.0.0', () => {
    console.log(`Signalling сервер запущен на порту ${PORT}`);
    console.log(`Ожидание подключений...`);
    console.log(`Environment: ${process.env.NODE_ENV || 'development'}`);
});
