const WebSocket = require('ws');

// Тест WebSocket подключения
console.log('Testing WebSocket connection to wss://videochat-aend.onrender.com...');

const ws = new WebSocket('wss://videochat-aend.onrender.com');

ws.on('open', function open() {
    console.log('✅ WebSocket connected successfully!');
    
    // Отправляем ping
    ws.send(JSON.stringify({ type: 'ping' }));
    
    // Запрашиваем список комнат
    ws.send(JSON.stringify({ type: 'get-rooms' }));
});

ws.on('message', function message(data) {
    try {
        const parsed = JSON.parse(data);
        console.log('📨 Received:', JSON.stringify(parsed, null, 2));
        
        if (parsed.type === 'pong') {
            console.log('🏓 Pong received!');
        } else if (parsed.type === 'rooms-list') {
            console.log('📋 Rooms list received:', parsed.rooms.length, 'rooms');
            ws.close();
        }
    } catch (error) {
        console.log('📨 Raw message:', data.toString());
    }
});

ws.on('error', function error(err) {
    console.error('❌ WebSocket error:', err.message);
});

ws.on('close', function close() {
    console.log('🔌 WebSocket connection closed');
    process.exit(0);
});

// Таймаут через 10 секунд
setTimeout(() => {
    console.log('⏰ Timeout reached');
    ws.close();
    process.exit(1);
}, 10000);
