const WebSocket = require('ws');
const http = require('http');
const express = require('express');

const app = express();
const server = http.createServer(app);

// WebSocket сервер с обработкой upgrade
const wss = new WebSocket.Server({
    server,
    verifyClient: (info, callback) => {
        callback(true); // Разрешаем все подключения
    }
});

// Обработка upgrade запросов
server.on('upgrade', (request, socket, head) => {
    wss.handleUpgrade(request, socket, head, (ws) => {
        wss.emit('connection', ws, request);
    });
});

// CORS для HTTP запросов
app.use((req, res, next) => {
    res.header('Access-Control-Allow-Origin', '*');
    res.header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.header('Access-Control-Allow-Headers', 'Content-Type');
    next();
});

// Health check endpoint для мониторинга
app.get('/health', (req, res) => {
    res.json({ status: 'ok', rooms: Array.from(rooms.keys()) });
});

// Хранилище для комнат
const rooms = new Map();

// Хранилище для подключений
const clients = new Map();

wss.on('connection', (ws) => {
    console.log('Новое подключение');
    
    // Heartbeat для поддержания соединения
    ws.isAlive = true;
    ws.on('pong', () => {
        ws.isAlive = true;
    });
    
    // Отправляем список комнат при подключении
    sendRoomsList(ws);
    
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

// Проверка живых соединений каждые 30 секунд
const heartbeatInterval = setInterval(() => {
    wss.clients.forEach((ws) => {
        if (ws.isAlive === false) {
            console.log('Соединение не отвечает, закрываем');
            return ws.terminate();
        }
        ws.isAlive = false;
        ws.ping();
    });
}, 30000);

function handleMessage(ws, data) {
    switch (data.type) {
        case 'join':
            handleJoin(ws, data);
            break;
        case 'offer':
            handleOffer(ws, data);
            break;
        case 'answer':
            handleAnswer(ws, data);
            break;
        case 'ice-candidate':
            handleIceCandidate(ws, data);
            break;
        case 'leave':
            handleLeave(ws, data);
            break;
        case 'get-rooms':
            sendRoomsList(ws);
            break;
        case 'chat-message':
            handleChatMessage(ws, data);
            break;
        default:
            console.log('Неизвестный тип сообщения:', data.type);
    }
}

function handleJoin(ws, data) {
    const { roomId, userId } = data;
    const isNewRoom = !rooms.has(roomId);
    
    if (isNewRoom) {
        rooms.set(roomId, new Set());
    }
    
    const room = rooms.get(roomId);
    room.add(userId);
    clients.set(userId, ws);
    ws.roomId = roomId;
    ws.userId = userId;
    
    console.log(`Пользователь ${userId} присоединился к комнате ${roomId}`);
    
    // Если новая комната - уведомить всех
    if (isNewRoom) {
        broadcastRoomsList();
    }
    
    // Уведомить других участников о новом пользователе
    const otherUsers = Array.from(room).filter(id => id !== userId);
    otherUsers.forEach(otherUserId => {
        const client = clients.get(otherUserId);
        if (client && client.readyState === WebSocket.OPEN) {
            client.send(JSON.stringify({
                type: 'user-joined',
                userId: userId  // ID нового пользователя
            }));
        }
    });
    
    // Отправить список участников
    ws.send(JSON.stringify({
        type: 'room-users',
        users: otherUsers
    }));
}

function handleOffer(ws, data) {
    const { roomId, targetUserId, offer } = data;
    const targetClient = clients.get(targetUserId);
    
    if (targetClient && targetClient.readyState === WebSocket.OPEN) {
        targetClient.send(JSON.stringify({
            type: 'offer',
            userId: ws.userId,
            offer: offer
        }));
    }
}

function handleAnswer(ws, data) {
    const { roomId, targetUserId, answer } = data;
    const targetClient = clients.get(targetUserId);
    
    if (targetClient && targetClient.readyState === WebSocket.OPEN) {
        targetClient.send(JSON.stringify({
            type: 'answer',
            userId: ws.userId,
            answer: answer
        }));
    }
}

function handleIceCandidate(ws, data) {
    const { roomId, targetUserId, candidate, sdpMid, sdpMLineIndex } = data;
    const targetClient = clients.get(targetUserId);
    
    if (targetClient && targetClient.readyState === WebSocket.OPEN) {
        targetClient.send(JSON.stringify({
            type: 'ice-candidate',
            userId: ws.userId,
            candidate: candidate,
            sdpMid: sdpMid,
            sdpMLineIndex: sdpMLineIndex
        }));
    }
}

function handleLeave(ws, data) {
    const { roomId, userId } = data;
    
    if (rooms.has(roomId)) {
        const room = rooms.get(roomId);
        room.delete(userId);
        
        if (room.size === 0) {
            rooms.delete(roomId);
            // Комната пуста - уведомить всех
            broadcastRoomsList();
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

function handleChatMessage(ws, data) {
    const { roomId, userId, message } = data;
    const timestamp = Date.now();

    console.log(`Chat message from ${userId} in room ${roomId}: ${message}`);

    // Отправляем сообщение всем участникам комнаты
    if (rooms.has(roomId)) {
        const room = rooms.get(roomId);
        room.forEach(otherUserId => {
            const client = clients.get(otherUserId);
            if (client && client.readyState === WebSocket.OPEN) {
                client.send(JSON.stringify({
                    type: 'chat-message',
                    userId: userId,
                    message: message,
                    timestamp: timestamp
                }));
            }
        });
    }
}

function handleDisconnect(ws) {
    const { roomId, userId } = ws;
    
    if (roomId && userId) {
        handleLeave(ws, { roomId, userId });
    }
}

// Отправить список комнат одному клиенту
function sendRoomsList(ws) {
    const roomsList = Array.from(rooms.entries()).map(([id, users]) => ({
        id: id,
        usersCount: users.size
    }));
    ws.send(JSON.stringify({
        type: 'rooms-list',
        rooms: roomsList
    }));
}

// Отправить список комнат всем клиентам
function broadcastRoomsList() {
    const roomsList = Array.from(rooms.entries()).map(([id, users]) => ({
        id: id,
        usersCount: users.size
    }));
    const message = JSON.stringify({
        type: 'rooms-list',
        rooms: roomsList
    });
    
    wss.clients.forEach(client => {
        if (client.readyState === WebSocket.OPEN) {
            client.send(message);
        }
    });
}

const PORT = process.env.PORT || 3000;
server.listen(PORT, '0.0.0.0', () => {
    console.log(`Signalling сервер запущен на порту ${PORT}`);
    console.log(`Ожидание подключений...`);
});
