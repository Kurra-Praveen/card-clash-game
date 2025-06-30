const io = require('socket.io-client');

const socket = io('http://192.168.31.210:3000', {
    transports: ['websocket'],
    reconnection: true,
    reconnectionAttempts: 5,
    reconnectionDelay: 1000
});

// Stat weights matching server
const statWeights = {
    runs: 1.0,
    wickets: 1.0,
    battingAverage: 1.5,
    strikeRate: 1.2,
    matchesPlayed: 0.8,
    centuries: 2.0,
    fiveWicketHauls: 2.5
};

// Helper to calculate weighted stat value
function getWeightedStatValue(card, stat) {
    const value = card[stat];
    return value * statWeights[stat];
}

// Helper to pick best stat for a card
function pickBestStat(card) {
    const stats = ['runs', 'wickets', 'battingAverage', 'strikeRate', 'matchesPlayed', 'centuries', 'fiveWicketHauls'];
    const weightedStats = stats.map(stat => ({
        stat,
        value: getWeightedStatValue(card, stat)
    }));
    // Sort by weighted value, pick top or random top 2 for variety
    weightedStats.sort((a, b) => b.value - a.value);
    const topStats = weightedStats.slice(0, 2);
    return topStats[Math.floor(Math.random() * topStats.length)];
}

socket.on('connect', () => {
    console.log('Mock player connected:', socket.id);
    socket.emit('joinRoom', '7C87AF');
    console.log('Mock player joined room: 1B425D');
});

socket.on('error', (error) => {
    console.error('Socket error:', error);
});

socket.on('gameData', (data) => {
    console.log('Received gameData:', JSON.stringify(data, null, 2));
    if (data.currentTurn === socket.id && data.cards.length > 0) {
        // Find best card and stat
        let bestCardIndex = 0;
        let bestWeightedValue = -Infinity;
        let bestStat = null;

        data.cards.forEach((card, index) => {
            const { stat, value } = pickBestStat(card);
            const weightedValue = value * statWeights[stat];
            if (weightedValue > bestWeightedValue) {
                bestWeightedValue = weightedValue;
                bestCardIndex = index;
                bestStat = { stat, value };
            }
        });

        if (bestStat) {
            socket.emit('submitStat', {
                roomCode: '7C87AF',
                cardIndex: bestCardIndex,
                stat: bestStat.stat,
                value: bestStat.value
            });
            console.log(`Submitted stat: cardIndex=${bestCardIndex}, ${bestStat.stat}=${bestStat.value}`);
        } else {
            console.error('No valid stat found for submission');
        }
    }
});

socket.on('roundResult', (data) => {
    console.log('Received roundResult:', JSON.stringify(data, null, 2));
});

socket.on('gameEnd', (data) => {
    console.log('Received gameEnd:', JSON.stringify(data, null, 2));
});

socket.on('disconnect', (reason) => {
    console.log('Mock player disconnected:', reason);
});