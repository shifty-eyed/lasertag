const { createApp } = Vue;

createApp({
    data() {
        return {
            connected: false,
            eventSource: null,

            gameState: {
                playing: false,
                timeLeftSeconds: 0,
                teamScores: {}
            },

            players: [],

            dispensers: {
                health: [],
                ammo: []
            },

            settings: {
                general: {
                    fragLimit: 10,
                    gameType: 'DM',
                    timeLimitMinutes: 15
                },
                players: {},
                dispensers: {
                    health: {
                        timeout: 60,
                        amount: 40
                    },
                    ammo: {
                        timeout: 60,
                        amount: 40
                    }
                }
            },

            teamNames: {
                0: 'Red',
                1: 'Blue',
                2: 'Green',
                3: 'Yellow',
                4: 'Magenta',
                5: 'Cyan'
            },
            teamColors: ['#DC143C', '#1E90FF', '#32CD32', '#FFD700', '#FF00FF', '#00CED1'],
            teamTextColors: ['#FFFFFF', '#FFFFFF', '#000000', '#000000', '#FFFFFF', '#000000'],

            logs: [],

            editingField: {
                playerId: null,
                fieldName: null
            },

            presets: [],
            selectedPreset: 'New...'
        };
    },

    computed: {
        onlineHealthDispensers() {
            return this.dispensers.health || [];
        },
        onlineAmmoDispensers() {
            return this.dispensers.ammo || [];
        },
        sortedTeamScores() {
            const teamTotals = this.players.reduce((acc, player) => {
                const teamId = player.teamId;

                if (!Object.prototype.hasOwnProperty.call(acc, teamId)) {
                    acc[teamId] = 0;
                }
                acc[teamId] += player.score || 0;
                return acc;
            }, {});

            const sorted = Object.fromEntries(
                Object.entries(teamTotals).sort((a, b) => b[1] - a[1])
            );

            if (this.isTeamBased) {
                return Object.fromEntries(
                    Object.entries(sorted).filter(([teamId]) => Number(teamId) === 0 || Number(teamId) === 1)
                );
            }
            return sorted;
        },
        gameStatus() {
            if (!this.connected) {
                return 'disconnected';
            }
            return this.gameState.playing ? 'game' : 'idle';
        },
        dispenserSettings() {
            return this.settings.dispensers || {
                health: { timeout: 60, amount: 40 },
                ammo: { timeout: 60, amount: 40 }
            };
        },
        gameStatusText() {
            switch (this.gameStatus) {
                case 'disconnected':
                    return '🔴 Disconnected';
                case 'game':
                    return '🎮 Game Active';
                case 'idle':
                    return '🟢 Idle';
                default:
                    return 'Unknown';
            }
        },
        isTeamBased() {
            const gameType = this.settings.general.gameType;
            return gameType === 'TEAM_DM' || gameType === 'CTF';
        },
        availableTeams() {
            if (this.isTeamBased) {
                return {
                    0: 'Red',
                    1: 'Blue'
                };
            }
            return this.teamNames;
        },
        gameTypeOptions() {
            return [
                { value: 'DM', label: 'DM' },
                { value: 'TEAM_DM', label: 'TEAM_DM' },
                { value: 'CTF', label: 'CTF' }
            ];
        }
    },

    watch: {
        'settings.general.gameType'() {
             if (this.isTeamBased) {
                this.players.forEach(player => {
                    if (player.teamId !== 0 && player.teamId !== 1) {
                        player.teamId = player.id % 2;
                        this.updatePlayer(player);
                    }
                });
             }
        }
    },

    methods: {
        connectSSE() {
            if (this.eventSource) {
                this.eventSource.close();
            }

            this.eventSource = new EventSource('/api/events');

            this.eventSource.addEventListener('isPlaying', (event) => {
                this.gameState.playing = JSON.parse(event.data);
                console.log('Got gameState.playing:', this.gameState.playing);
            });

            this.eventSource.addEventListener('players', (event) => {
                const incomingPlayers = JSON.parse(event.data);
                console.log('Got players:', incomingPlayers);

                const updatedPlayers = incomingPlayers.map(incomingPlayer => {
                    const existingPlayer = this.players.find(p => p.id === incomingPlayer.id);
                    
                    if (existingPlayer) {
                        const merged = { ...incomingPlayer };
                        if (this.editingField.playerId === incomingPlayer.id) {
                            const editedField = this.editingField.fieldName;
                            if (editedField && existingPlayer.hasOwnProperty(editedField)) {
                                merged[editedField] = existingPlayer[editedField];
                            }
                        }
                        return merged;
                    } else {
                        return incomingPlayer;
                    }
                });

                this.players = updatedPlayers;
            });

            this.eventSource.addEventListener('timeLeft', (event) => {
                this.gameState.timeLeftSeconds = Number(event.data);
                this.gameState.playing = this.gameState.timeLeftSeconds > 0;
                console.log('Got timeLeft:', this.gameState.timeLeftSeconds);
            });

            this.eventSource.addEventListener('dispensers', (event) => {
                this.dispensers = JSON.parse(event.data);
                console.log('Got dispensers:', this.dispensers);
            });

            this.eventSource.addEventListener('settings', (event) => {
                const settings = JSON.parse(event.data);
                if (settings.presetName) {
                    this.selectedPreset = settings.presetName;
                }
                this.settings = settings;
                console.log('Got settings:', this.settings);
            });

            this.eventSource.addEventListener('log', (event) => {
                const logMessage = JSON.parse(event.data);
                console.log('Got log:', logMessage);
                console.log('Total logs:', this.logs.length);
                this.logs.push(logMessage);
                // Auto-scroll to bottom
                this.$nextTick(() => {
                    if (this.$refs.logContent) {
                        this.$refs.logContent.scrollTop = this.$refs.logContent.scrollHeight;
                    }
                });
            });

            this.eventSource.onopen = () => {
                this.connected = true;
                console.log('SSE connection established');
            };

            this.eventSource.onerror = (error) => {
                console.error('SSE error:', error);
                this.connected = false;
                
                // Attempt to reconnect after 3 seconds
                setTimeout(() => {
                    if (!this.connected) {
                        console.log('Attempting to reconnect...');
                        this.connectSSE();
                    }
                }, 3000);
            };
        },

        async startGame() {
            try {
                const response = await fetch('/api/game/start', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        timeLimit: this.settings.general.timeLimitMinutes,
                        fragLimit: this.settings.general.fragLimit,
                        gameType: this.settings.general.gameType
                    })
                });
                
                if (!response.ok) {
                    throw new Error('Failed to start game');
                }
                
                console.log('Game started');
            } catch (error) {
                console.error('Error starting game:', error);
                alert('Failed to start game');
            }
        },

        async endGame() {
            try {
                const response = await fetch('/api/game/end', {
                    method: 'POST'
                });
                
                if (!response.ok) {
                    throw new Error('Failed to end game');
                }
                
                console.log('Game ended');
            } catch (error) {
                console.error('Error ending game:', error);
                alert('Failed to end game');
            }
        },

        async updatePlayer(player) {
            try {
                const response = await fetch(`/api/players/${player.id}`, {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        name: player.name,
                        teamId: player.teamId,
                        damage: player.damage,
                        bulletsMax: player.bulletsMax
                    })
                });
                
                if (!response.ok) {
                    throw new Error('Failed to update player');
                }
                
                console.log('Player updated:', player.name);
            } catch (error) {
                console.error('Error updating player:', error);
                alert('Failed to update player');
            }
        },

        async updateDispensers(type) {
            const typeKey = type.toLowerCase();
            const settings = this.settings.dispensers[typeKey];

            try {
                const response = await fetch(`/api/dispensers/${type}`, {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        timeout: settings.timeout,
                        amount: settings.amount
                    })
                });
                
                if (!response.ok) {
                    throw new Error('Failed to update dispensers');
                }
            } catch (error) {
                console.error('Error updating dispensers:', error);
                alert('Failed to update dispensers');
            }
        },

        formatTime(seconds) {
            const minutes = Math.floor(seconds / 60);
            const secs = seconds % 60;
            return `${String(minutes).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
        },

        getTeamName(teamId) {
            return this.teamNames[teamId] || 'Unknown';
        },

        getTeamColor(teamId) {
            return this.teamColors[teamId] || '#888888';
        },

        getTeamTextColor(teamId) {
            return this.teamTextColors[teamId] || '#000000';
        },

        isRespawnPending(player) {
            return player.online &&
                player.health === 0 &&
                player.assignedRespawnPoint >= 0;
        },

        async triggerRespawnPoint(player) {
            try {
                const response = await fetch(
                    `/api/players/${player.id}/devevent?type=6&payload=${player.assignedRespawnPoint}`,
                    { method: 'POST' }
                );
            } catch (error) {
                alert('Failed to send respawn point event');
            }
        },

        clearLogs() {
            this.logs = [];
        },

        getLogLevelClass(log) {
            if (log.includes('ERROR')) {
                return 'log-error';
            } else if (log.includes('WARN')) {
                return 'log-warn';
            } else if (log.includes('INFO')) {
                return 'log-info';
            } else if (log.includes('DEBUG')) {
                return 'log-debug';
            }
            return '';
        },

        onPlayerFieldFocus(player, fieldName) {
            this.editingField.playerId = player.id;
            this.editingField.fieldName = fieldName;
        },

        onPlayerFieldBlur(player, fieldName) {
            if (this.editingField.playerId === player.id && this.editingField.fieldName === fieldName) {
                this.editingField.playerId = null;
                this.editingField.fieldName = null;
            }
        },

        async fetchPresets() {
            try {
                const response = await fetch('/api/presets');
                if (response.ok) {
                    this.presets = await response.json();
                    console.log('Loaded presets:', this.presets);
                }
            } catch (error) {
                console.error('Error fetching presets:', error);
            }
        },

        async loadPreset() {
            if (this.selectedPreset === 'New...') {
                return;
            }

            try {
                const response = await fetch(`/api/presets/${encodeURIComponent(this.selectedPreset)}/load`, {
                    method: 'POST'
                });
                
                if (!response.ok) {
                    throw new Error('Failed to load preset');
                }
                
                console.log('Preset loaded:', this.selectedPreset);
            } catch (error) {
                console.error('Error loading preset:', error);
                alert('Failed to load preset');
            }
        },

        async savePreset() {
            let presetName = this.selectedPreset;
            
            if (presetName === 'New...') {
                presetName = prompt('Enter preset name:');
                if (!presetName || presetName.trim() === '') {
                    return;
                }
                presetName = presetName.trim();
            }

            try {
                const response = await fetch(`/api/presets/${encodeURIComponent(presetName)}`, {
                    method: 'POST'
                });
                
                if (!response.ok) {
                    throw new Error('Failed to save preset');
                }
                
                console.log('Preset saved:', presetName);
                await this.fetchPresets();
                this.selectedPreset = presetName;
            } catch (error) {
                console.error('Error saving preset:', error);
                alert('Failed to save preset');
            }
        }
    },

    mounted() {
        this.connectSSE();
        this.fetchPresets();
    },

    beforeUnmount() {
        if (this.eventSource) {
            this.eventSource.close();
        }
    }
}).mount('#app');

