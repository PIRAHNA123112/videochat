const WebSocket = require('ws');
const http = require('http');
const express = require('express');

const app = express();
const server = http.createServer(app);

// WebSocket сервер
const wss = new WebSocket.Server({ server });

// Middleware для JSON
app.use(express.json());

// CORS
app.use((req, res, next) => {
    res.header('Access-Control-Allow-Origin', '*');
    res.header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.header('Access-Control-Allow-Headers', 'Content-Type');
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
wss.on('connection', (ws) => {
    console.log('New WebSocket connection');
    
    ws.on('message', (message) => {
        try {
            const data = JSON.parse(message);
            handleMessage(ws, data);
        } catch (error) {
            console.error('Error parsing message:', error);
        }
    });

    ws.on('close', () => {
        handleDisconnect(ws);
    });

    ws.on('error', (error) => {
        console.error('WebSocket error:', error);
    });
});

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
    const { roomId, userId } = data;
    
    if (!rooms.has(roomId)) {
        rooms.set(roomId, new Set());
    }
    
    const room = rooms.get(roomId);
    room.add(userId);
    clients.set(userId, ws);
    ws.roomId = roomId;
    ws.userId = userId;
    
    console.log(`User ${userId} joined room ${roomId}`);
    
    // Отправить список комнат
    broadcastRoomsList();
    
    // Уведомить других участников
    const otherUsers = Array.from(room).filter(id => id !== userId);
    otherUsers.forEach(otherUserId => {
        const client = clients.get(otherUserId);
        if (client && client.readyState === WebSocket.OPEN) {
            client.send(JSON.stringify({
                type: 'user-joined',
                userId: userId
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
