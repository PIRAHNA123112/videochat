const WebSocket = require('ws');
const http = require('http');
const express = require('express');

const app = express();
const server = http.createServer(app);

// WebSocket сервер
const wss = new WebSocket.Server({ server });

// Middleware для JSON
app.use(express.json());

// CORS с поддержкой WebSocket
app.use((req, res, next) => {
    res.header('Access-Control-Allow-Origin', '*');
    res.header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.header('Access-Control-Allow-Headers', 'Content-Type, WebSocket-Protocol, Sec-WebSocket-Key, Sec-WebSocket-Version, Sec-WebSocket-Protocol');
    next();
});

// Health check
app.get('/health', (req, res) => {
    res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

// Root
app.get('/', (req, res) => {
    res.json({ message: 'WebRTC Signalling Server', status: 'running' });
});

// Хранилище
const rooms = new Map();
const clients = new Map();

// WebSocket подключение
wss.on('connection', (ws, req) => {
    console.log('🔌 New WebSocket connection from:', req.url);
    console.log('🌐 Headers:', req.headers);
    console.log('📡 Remote address:', req.socket.remoteAddress);
    console.log('📱 User-Agent:', req.headers['user-agent']);
    
    // Устанавливаем флаг для heartbeat
    ws.isAlive = true;
    ws.connectTime = Date.now();
    
    // Определяем тип клиента по User-Agent
    const userAgent = req.headers['user-agent'] || '';
    ws.clientType = userAgent.includes('Android') ? 'android' : 
                    userAgent.includes('iPhone') ? 'ios' : 
                    userAgent.includes('Mobile') ? 'mobile' : 'desktop';
    
    console.log(`📱 Client type detected: ${ws.clientType}`);
    
    // Обработка ping/pong с улучшенным логированием
    ws.on('pong', () => {
        ws.isAlive = true;
        ws.lastPongTime = Date.now();
        console.log(`🏓 Received pong from ${ws.userId || 'unknown'} client`);
    });
    
    // Обработка ошибок с детальной диагностикой
    ws.on('error', (error) => {
        console.error(`❌ WebSocket error for ${ws.userId || 'unknown'}:`, error.message);
        console.error(`📍 Stack trace:`, error.stack);
        
        // Отправляем ошибку клиенту если возможно
        if (ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify({
                type: 'error',
                error: 'Internal server error',
                code: 'INTERNAL_ERROR'
            }));
        }
    });
    
    // Обработка отключения с улучшенным логированием
    ws.on('close', (code, reason) => {
        console.log(`🔌 WebSocket closed: ${code} - ${reason}`);
        console.log(`👤 User: ${ws.userId || 'unknown'}, Room: ${ws.roomId || 'none'}`);
        console.log(`⏱️ Connection duration: ${Date.now() - ws.connectTime}ms`);
        
        // Удаляем из всех комнат с уведомлением
        if (ws.roomId && ws.userId) {
            const room = rooms.get(ws.roomId);
            if (room) {
                room.delete(ws.userId);
                console.log(`👋 Removed ${ws.userId} from room ${ws.roomId} (${room.size} users left)`);
                
                // Уведомляем остальных участников
                room.forEach(userId => {
                    const client = clients.get(userId);
                    if (client && client.readyState === WebSocket.OPEN && client !== ws) {
                        client.send(JSON.stringify({
                            type: 'user-left',
                            userId: ws.userId,
                            timestamp: Date.now()
                        }));
                    }
                });
                
                // Удаляем комнату если пустая
                if (room.size === 0) {
                    rooms.delete(ws.roomId);
                    console.log(`🗑️ Deleted empty room: ${ws.roomId}`);
                }
            }
        }
        
        clients.delete(ws.userId);
    });
    
    ws.on('message', (message) => {
        try {
            const data = JSON.parse(message);
            
            // Логируем все сообщения для диагностики
            console.log(`📨 Message from ${ws.userId || 'unknown'} (${ws.clientType}): ${data.type}`);
            
            // Обработка ping сообщений с улучшенной поддержкой мобильных
            if (data.type === 'ping') {
                ws.isAlive = true;
                ws.lastPingTime = Date.now();
                
                // Для мобильных клиентов отправляем дополнительную информацию
                const pongResponse = {
                    type: 'pong',
                    timestamp: Date.now(),
                    serverTime: Date.now(),
                    clientType: ws.clientType
                };
                
                // Для мобильных добавляем информацию о сети
                if (ws.clientType === 'android' || ws.clientType === 'mobile') {
                    pongResponse.networkOptimization = true;
                    pongResponse.recommendedHeartbeat = ws.clientType === 'android' ? 15000 : 10000;
                }
                
                ws.send(JSON.stringify(pongResponse));
                return;
            }
            
            // Добавляем метаданные клиента к сообщениям
            data.clientMetadata = {
                clientType: ws.clientType,
                connectTime: ws.connectTime,
                networkType: ws.networkType
            };
            
            handleMessage(ws, data);
        } catch (error) {
            console.error(`❌ Error parsing message from ${ws.userId || 'unknown'}:`, error.message);
            console.error(`📍 Raw message:`, message);
            
            // Отправляем ошибку клиенту
            if (ws.readyState === WebSocket.OPEN) {
                ws.send(JSON.stringify({
                    type: 'error',
                    error: 'Invalid message format',
                    code: 'INVALID_MESSAGE'
                }));
            }
        }
    });

    ws.on('close', () => {
        handleDisconnect(ws);
    });

    ws.on('error', (error) => {
        console.error('WebSocket error:', error);
    });
});

// Heartbeat интервал - адаптивный для разных типов клиентов
const heartbeatInterval = setInterval(() => {
    const now = Date.now();
    let activeCount = 0;
    let terminatedCount = 0;
    
    wss.clients.forEach((ws) => {
        // Адаптивный таймаут для разных типов клиентов
        const timeout = ws.clientType === 'android' ? 45000 : // 45с для Android
                        ws.clientType === 'mobile' ? 35000 :  // 35с для мобильных
                        30000; // 30с для desktop
        
        if (ws.isAlive === false || (now - (ws.lastPongTime || ws.connectTime)) > timeout) {
            console.log(`💔 Terminating inactive connection: ${ws.userId || 'unknown'} (${ws.clientType})`);
            terminatedCount++;
            return ws.terminate(1000, 'Heartbeat timeout');
        }
        
        ws.isAlive = false;
        ws.ping();
        activeCount++;
    });
    
    if (activeCount > 0 || terminatedCount > 0) {
        console.log(`💓 Heartbeat check: ${activeCount} active, ${terminatedCount} terminated`);
    }
}, 15000); // Проверяем каждые 15 секунд для мобильной оптимизации

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
            console.log('Unknown message type:', data.type);
    }
}

function handleJoin(ws, data) {
    const { roomId, userId, roomPassword, timestamp, clientType, networkType } = data;
    
    console.log(`Join request: Room=${roomId}, User=${userId}, Client=${clientType}, Network=${networkType}`);
    
    // Валидация входных данных
    if (!roomId || roomId.trim() === '') {
        console.log('❌ Invalid roomId');
        ws.send(JSON.stringify({
            type: 'error',
            error: 'Invalid room ID',
            code: 'INVALID_ROOM_ID'
        }));
        return;
    }
    
    if (!userId || userId.trim() === '') {
        console.log('❌ Invalid userId');
        ws.send(JSON.stringify({
            type: 'error',
            error: 'Invalid user ID',
            code: 'INVALID_USER_ID'
        }));
        return;
    }
    
    // Валидация пароля комнаты (простая защита)
    const validPasswords = ['1234', 'password', 'room', 'chat']; // Можно расширить
    if (roomPassword && !validPasswords.includes(roomPassword)) {
        console.log(`❌ Invalid room password: ${roomPassword}`);
        ws.send(JSON.stringify({
            type: 'error',
            error: 'Invalid room password',
            code: 'INVALID_PASSWORD'
        }));
        return;
    }
    
    // Проверяем существует ли комната
    if (!rooms.has(roomId)) {
        rooms.set(roomId, new Set());
        console.log(`📝 Created new room: ${roomId}`);
    }
    
    const room = rooms.get(roomId);
    
    // Проверяем не находится ли пользователь уже в комнате
    if (room.has(userId)) {
        console.log(`⚠️ User ${userId} already in room ${roomId}`);
        // Удаляем старое соединение если есть
        const oldWs = clients.get(userId);
        if (oldWs && oldWs !== ws) {
            oldWs.close(1000, 'Duplicate connection');
        }
    }
    
    // Добавляем пользователя в комнату
    room.add(userId);
    clients.set(userId, ws);
    ws.roomId = roomId;
    ws.userId = userId;
    ws.joinTime = timestamp || Date.now();
    ws.clientType = clientType || 'unknown';
    ws.networkType = networkType || 'unknown';
    
    console.log(`✅ User ${userId} joined room ${roomId} (${room.size} users)`);
    
    // Отправить подтверждение успешного входа
    ws.send(JSON.stringify({
        type: 'join-success',
        roomId: roomId,
        userId: userId,
        timestamp: Date.now()
    }));
    
    // Отправить список комнат всем
    broadcastRoomsList();
    
    // Уведомить других участников о новом пользователе
    const otherUsers = Array.from(room).filter(id => id !== userId);
    otherUsers.forEach(otherUserId => {
        const client = clients.get(otherUserId);
        if (client && client.readyState === WebSocket.OPEN) {
            client.send(JSON.stringify({
                type: 'user-joined',
                userId: userId,
                timestamp: Date.now()
            }));
        }
    });
    
    // Отправить новому пользователю список участников
    ws.send(JSON.stringify({
        type: 'room-users',
        users: otherUsers,
        timestamp: Date.now()
    }));
    
    // Если в комнате уже 2 пользователя, уведомляем о готовности звонка
    if (room.size === 2) {
        console.log(`📞 Room ${roomId} is now ready for call (2 users)`);
        room.forEach(uid => {
            const client = clients.get(uid);
            if (client && client.readyState === WebSocket.OPEN) {
                client.send(JSON.stringify({
                    type: 'call-ready',
                    users: Array.from(room),
                    timestamp: Date.now()
                }));
            }
        });
    }
}

function handleOffer(ws, data) {
    const { targetUserId, offer } = data;
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
    const { targetUserId, answer } = data;
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
    const { targetUserId, candidate, sdpMid, sdpMLineIndex } = data;
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
            broadcastRoomsList();
        } else {
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
    console.log(`User ${userId} left room ${roomId}`);
}

function handleChatMessage(ws, data) {
    const { roomId, userId, message } = data;
    const timestamp = Date.now();

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

// Запуск сервера
const PORT = process.env.PORT || 3000;
server.listen(PORT, '0.0.0.0', () => {
    console.log(`Server running on port ${PORT}`);
    console.log(`Environment: ${process.env.NODE_ENV || 'development'}`);
});
