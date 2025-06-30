const fs = require('fs');

// Load and parse the JSON file
let rawData = fs.readFileSync('odiStats.json');
let data = JSON.parse(rawData);

// Loop through each player object and transform it
data.forEach(player => {
  player.centuries = String(parseInt(player['100']) + parseInt(player['200'])*2);
  delete player['100'];
  delete player['200'];
});

// Write the updated object back to the file
fs.writeFileSync('player_stats.json', JSON.stringify(data, null, 2));
console.log('Before update:', JSON.parse(rawData)[0]);
console.log('After update:', data[0]);
console.log('File updated with centuries!');
