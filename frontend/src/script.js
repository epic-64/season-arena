import {
    animateBuffApplied,
    animateDamageDealt,
    animateHeal,
    animateResourceDrained,
    animateSkillUsed,
    animateTurnStart,
    animateCharacterActivated,
    animateBattleEnd
} from './animations.js';

import {statusEmojis} from './emojiMappings.js';
import {ActorClass} from '../generated/types.js';
import {CombatEventType} from './types-local.js';
import {createElement} from './utils.js';
import {createPlayback} from './playback.js';

async function loadLog() {
    try {
        // const response = await fetch('../output/battle_log.json');
        const response = await fetch('http://localhost:8080/combat/example');
        return await response.json();
    } catch (error) {
        console.error('Error loading log:', error);
    }
}

// === Actor Setup ===
function getPortraitSrc(actorClass)
{
    const classToFile = {
        [ActorClass.Mage]: 'mage.png',
        [ActorClass.Cleric]: 'druid.png',
        [ActorClass.Hunter]: 'hunter2.png',
        [ActorClass.Paladin]: 'paladin.png',
        [ActorClass.Bard]: 'bard.png',
        [ActorClass.Fishman]: 'fishman.png',
        [ActorClass.AbyssalDragon]: 'abyss_dragon.png',
    };

    const lowerName = actorClass.toLowerCase();
    const file = classToFile[actorClass] || `${lowerName}.png`; // default to lowercase name

    return `assets/images/portraits/${file}`;
}

function initializeActors(snapshot)
{
    const heroes = document.getElementById('heroes');
    const enemies = document.getElementById('enemies');
    // Clear previous actors to avoid duplicate portraits when rebuilding state
    heroes.innerHTML = '';
    enemies.innerHTML = '';
    snapshot.actors.forEach(actor => {
        const actorDiv = createElement('div', {
            id: `actor-${actor.name}`,
            classes: ['actor']
        });

        const portraitImg = createElement('img', {
            classes: ['portrait'],
        });
        portraitImg.src = getPortraitSrc(actor.actorClass);
        portraitImg.alt = actor.actorClass;

        const namePlate = createElement('div', {
            text: actor.name,
            classes: ['actor-name']
        });

        const healthBar = createElement('div', {
            classes: ['health-bar'],
            styles: { width: `${(actor.hp / actor.maxHp) * 100}%` }
        });
        const healthBarValue = createElement('span', {
            classes: ['bar-value'],
            text: `${actor.hp} / ${actor.maxHp}`
        });
        const healthBarContainer = createElement('div', { classes: ['resource-bar-container'] });
        healthBarContainer.append(healthBar);
        healthBarContainer.append(healthBarValue);

        const manaBar = createElement('div', {
            classes: ['mana-bar'],
            styles: { width: actor.maxMana > 0 ? `${(actor.mana / actor.maxMana) * 100}%` : '0%' }
        });
        const manaBarValue = createElement('span', {
            classes: ['bar-value'],
            text: `${actor.mana} / ${actor.maxMana}`
        });
        const manaBarContainer = createElement('div', { classes: ['resource-bar-container'] });
        manaBarContainer.append(manaBar);
        manaBarContainer.append(manaBarValue);

        const statusEffects = createElement('div', { classes: ['status-effects'] });

        actorDiv.appendChild(namePlate);
        actorDiv.appendChild(portraitImg);
        actorDiv.appendChild(healthBarContainer);
        actorDiv.appendChild(manaBarContainer);
        actorDiv.appendChild(statusEffects); // below bar, visible (container has overflow hidden)

        (actor.team === 0 ? heroes : enemies).appendChild(actorDiv);
    });
}

/**
 * @param {HTMLElement} container
 * @param {StatBuffSnapshot[]|ResourceTickSnapshot[]|StatOverrideSnapshot[]} effects
 * @param {(effect: StatBuffSnapshot|ResourceTickSnapshot) => string | undefined } getTitle
 */
function renderStatusEffects(container, effects, getTitle) {
    effects.forEach(effect => renderStatusEffect(container, effect, getTitle));
}

/**
 * @param {HTMLElement} container
 * @param {StatBuffSnapshot|ResourceTickSnapshot|StatOverrideSnapshot} effect
 * @param {(effect: StatBuffSnapshot|ResourceTickSnapshot|StatOverrideSnapshot) => string } getTitle
 */
function renderStatusEffect(container, effect, getTitle) {
    const symbol = statusEmojis[effect.id] || '✨';
    const effectEmoji = createElement('span', {
        classes: ['status-effect']
    });
    // Add identifying attribute for animation targeting
    effectEmoji.setAttribute('data-effect-id', effect.id);
    effectEmoji.textContent = symbol;

    // value indicator (top left)
    const value = 0; // todo: parse actual value from statusEffect
    if (value) {
        const valueSpan = createElement('span', {
            classes: ['effect-value']
        });
        valueSpan.textContent = value;
        effectEmoji.appendChild(valueSpan);
    }

    // Duration indicator (bottom right)
    if (effect.duration) {
        const durationSpan = createElement('span', {
            classes: ['effect-duration']
        });
        durationSpan.textContent = effect.duration;
        effectEmoji.appendChild(durationSpan);
    }

    try {
        if (getTitle) {
            effectEmoji.title = getTitle(effect);
        }
    } catch (e) {
        console.error('Error generating effect title:', e);
    }
    container.appendChild(effectEmoji);
}

/**
 * @param {BattleSnapshot} snapshot
 * @return {void}
 */
function updateAllActorDisplays(snapshot) {
    snapshot.actors.forEach(updateActorDisplay);
}

/**
 * @param {ActorSnapshot} actor
 * @return {void}
 */
function updateActorDisplay(actor)
{
    const actorDiv = document.getElementById(`actor-${actor.name}`);
    if (!actorDiv) {
        console.error(`Actor display update: element not found (actor-${actor.name})`);
        return;
    }

    // Update health bar
    const healthBarContainer = actorDiv.querySelectorAll('.resource-bar-container')[0];
    const healthBar = healthBarContainer.querySelector('.health-bar');
    const healthBarValue = healthBarContainer.querySelector('.bar-value');
    if (!healthBar) {
        console.error('Health bar element not found');
        return;
    }

    const percent = actor.maxHp > 0 ? (actor.hp / actor.maxHp) : 0;
    healthBar.style.width = `${percent * 100}%`;
    healthBar.classList.remove('health-bar-yellow', 'health-bar-red');
    if (percent < 0.33) {
        healthBar.classList.add('health-bar-red');
    } else if (percent < 0.66) {
        healthBar.classList.add('health-bar-yellow');
    }
    // Update health value text
    if (healthBarValue) {
        healthBarValue.textContent = `${actor.hp} / ${actor.maxHp}`;
    }
    // Update mana bar
    const manaBarContainer = actorDiv.querySelectorAll('.resource-bar-container')[1];
    const manaBar = manaBarContainer.querySelector('.mana-bar');
    const manaBarValue = manaBarContainer.querySelector('.bar-value');
    const manaPercent = actor.maxMana > 0 ? (actor.mana / actor.maxMana) : 0;
    manaBar.style.width = `${manaPercent * 100}%`;
    manaBar.classList.remove('mana-bar-low');
    if (manaPercent < 0.33) {
        manaBar.classList.add('mana-bar-low');
    }
    // Update mana value text
    if (manaBarValue) {
        manaBarValue.textContent = `${actor.mana} / ${actor.maxMana}`;
    }


    // Update status effects
    const statusEffects = actorDiv.querySelector('.status-effects');
    if (!statusEffects) {
        console.error('Status effects container not found');
        return;
    }

    statusEffects.innerHTML = '';
    renderStatusEffects(statusEffects, actor.statBuffs, buff => buff.statChanges ? `+${JSON.stringify(buff.statChanges)} (${buff.duration || 0}t)` : undefined);
    renderStatusEffects(statusEffects, actor.resourceTicks, tick => tick.resourceChanges ? `${JSON.stringify(tick.resourceChanges)} (${tick.duration || 0}t)` : undefined);
    renderStatusEffects(statusEffects, actor.statOverrides, override => override.statOverrides ? `=${JSON.stringify(override.statOverrides)}` : undefined);
}

const playback = createPlayback();

// Helper: update play/pause toggle button label/state
function updatePlayToggleButton()
{
    const btn = document.getElementById('btn-toggle-play');
    if (!btn) {
        return;
    }

    btn.classList.remove('is-playing', 'is-ended');

    if (playback.playing) {
        btn.textContent = 'Pause';
        btn.classList.add('is-playing');
    } else if (playback.index >= playback.events.length - 1 && playback.events.length > 0) {
        btn.textContent = 'Replay';
        btn.classList.add('is-ended');
    } else {
        btn.textContent = 'Play';
    }
}

/**
 * @param {CompactCombatEvent} event
 */
function animateEvent(event) {
    switch (event.type) {
        case CombatEventType.BattleStart:
            // No animation for BattleStart
            break;
        case CombatEventType.TurnStart:
            animateTurnStart(event);
            break;
        case CombatEventType.CharacterActivated:
            animateCharacterActivated(event);
            break;
        case CombatEventType.SkillUsed:
            animateSkillUsed(event);
            break;
        case CombatEventType.DamageDealt:
            animateDamageDealt(event);
            break;
        case CombatEventType.ResourceDrained:
            animateResourceDrained(event);
            break;
        case CombatEventType.Healed:
            animateHeal(event);
            break;
        case CombatEventType.BuffApplied:
            animateBuffApplied(event);
            break;
        case CombatEventType.BuffRemoved:
            animateBuffApplied(event);
            break;
        case CombatEventType.BattleEnd:
            animateBattleEnd(event);
            break;
        default:
            console.error('Unhandled event type', event);
            break;
    }
}

// === Main Runner ===
async function runBattleApplication() {
    const logData = await loadLog();

    const initialSnapshotEvent = logData.find(e => e.type === CombatEventType.BattleStart);
    if (!initialSnapshotEvent?.snapshot) {
        console.error("Initial snapshot not found in log.");
        return;
    }
    playback.initialSnapshot = initialSnapshotEvent.snapshot;
    playback.currentSnapshot = JSON.parse(JSON.stringify(playback.initialSnapshot));
    initializeActors(playback.initialSnapshot);
    updateAllActorDisplays(playback.initialSnapshot);
    playback.init(logData);
    wireControls();
    playback.play();
}

function wireControls() {
    const btnToggle = document.getElementById('btn-toggle-play');
    const btnPrev = document.getElementById('btn-prev');
    const btnNext = document.getElementById('btn-next');
    const speedSel = document.getElementById('play-speed');

    btnToggle.onclick = () => playback.toggle();
    btnPrev.onclick = () => playback.stepBack();
    btnNext.onclick = () => playback.stepForward();
    speedSel.onchange = e => playback.setSpeed(parseFloat(e.target.value));

    updatePlayToggleButton();
}

export { runBattleApplication, updateAllActorDisplays, updatePlayToggleButton, animateEvent, initializeActors };
