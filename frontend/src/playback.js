// Playback controller module for battle log playback
// Handles play, pause, step, and state rebuild

import { initializeActors, updateAllActorDisplays, animateEvent, updatePlayToggleButton } from './script.js';
import { logEventUnified } from './eventLog.js';
import {CombatEventType} from './types-local.js';

function createPlayback() {
    return {
        events: [],
        index: -1,          // last executed index
        playing: false,
        timer: null,
        baseInterval: 500,
        speed: 1,
        initialSnapshot: null,
        currentSnapshot: null,
        snapshotHistory: [],

        init(log) {
            this.events = log.filter(e => e.type !== CombatEventType.BuffExpired)
        },

        play() {
            if (this.playing) return;
            this.playing = true;
            updatePlayToggleButton();
            this.scheduleNext();
        },

        pause() {
            this.playing = false;
            if (this.timer) clearTimeout(this.timer);
            updatePlayToggleButton();
        },

        toggle() {
            if (this.playing) {
                this.pause();
            } else {
                // If at end, reset to allow replay
                if (this.index >= this.events.length - 1) {
                    this.reset();
                }
                this.play();
            }
        },

        reset() {
            this.pause();
            this.index = -1;
            this.snapshotHistory = []; // Clear history to prevent memory leak
            this.currentSnapshot = null; // Optionally reset current snapshot for a clean state
            this.rebuildState();
            this.replayLogs();
        },

        scheduleNext() {
            if (!this.playing) return;
            if (this.index >= this.events.length - 1) {
                this.pause();
                return;
            }
            this.stepForward();
            this.timer = setTimeout(() => this.scheduleNext(), this.baseInterval / this.speed);
        },

        // Helper to apply delta to the current snapshot
        applyDeltaToSnapshot(delta, snapshot) {
            if (!delta || !delta.actors) return;
            delta.actors.forEach(deltaActor => {
                const actor = snapshot.actors.find(a => a.name === deltaActor.name);
                if (actor) {
                    Object.assign(actor, deltaActor);
                }
            });
        },

        stepForward() {
            if (this.index >= this.events.length - 1) {
                return;
            }

            // Save a deep copy of the current snapshot for stepBack
            if (this.currentSnapshot) {
                this.snapshotHistory.push(JSON.parse(JSON.stringify(this.currentSnapshot)));
            } else if (this.initialSnapshot) {
                this.currentSnapshot = JSON.parse(JSON.stringify(this.initialSnapshot));
                this.snapshotHistory.push(JSON.parse(JSON.stringify(this.currentSnapshot)));
            }

            this.index++;
            const evt = this.events[this.index];
            logEventUnified(evt);

            if (evt.snapshot) {
                this.currentSnapshot = JSON.parse(JSON.stringify(evt.snapshot));
            } else {
                this.applyDeltaToSnapshot(evt.delta, this.currentSnapshot);
            }

            updateAllActorDisplays(this.currentSnapshot);
            animateEvent(evt);

            if (this.index >= this.events.length - 1) {
                if (this.playing) this.pause();
            }
        },

        stepBack() {
            if (this.index <= 0) {
                return;
            }

            this.index--; // Decrement index before replaying logs
            this.replayLogs();

            if (this.snapshotHistory.length > 0) {
                this.currentSnapshot = this.snapshotHistory.pop();
                updateAllActorDisplays(this.currentSnapshot);
            }

            this.pause()
        },

        rebuildState() {
            // Determine snapshot to use
            const snap = this.findSnapshotForIndex(this.index);
            if (snap) {
                updateAllActorDisplays(snap);
            } else if (this.initialSnapshot) {
                initializeActors(this.initialSnapshot);
            }

            updatePlayToggleButton();
        },

        replayLogs() {
            // Clear logs
            const logContainer = document.getElementById('action-log');
            logContainer.innerHTML = '';

            // Replay (log only, no animations) prior events for log context
            for (let i = 0; i <= this.index; i++) {
                const evt = this.events[i];
                logEventUnified(evt)
            }
        },

        findSnapshotForIndex(i) {
            if (i < 0) {
                return this.initialSnapshot;
            }

            for (let k = i; k >= 0; k--) {
                if (this.events[k].snapshot) {
                    return this.events[k].snapshot;
                }
            }
            return this.initialSnapshot;
        },

        setSpeed(multiplier) {
            this.speed = multiplier;
            if (this.playing) {
                this.pause();
                this.play();
            }
        }
    };
}

export { createPlayback };
