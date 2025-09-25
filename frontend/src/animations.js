// Animation-related functions extracted from script.js
// Handles visual effects for combat events

import { createElement } from './utils.js';
import { skillEmojis } from './emojiMappings.js';

/**
 * @param {CompactCombatEvent_SkillUsed} event - The skill used event object
 * @return {void}
 */
function animateSkillUsed(event) {
    const actorEl = document.getElementById(`actor-${event.actor}`);
    if (!actorEl) {
        console.error(`Skill used animation: actor element not found (actor-${event.actor})`);
        return;
    }

    // Animate skill emoji for each target
    event.targets.forEach(targetName => {
        const targetEl = document.getElementById(`actor-${targetName}`);
        if (!targetEl) return;

        const skillEmoji = createElement('div', {
            text: skillEmojis[event.skill],
            styles: {
                position: 'absolute',
                fontSize: '2em',
                transition: 'transform 0.5s, opacity 0.5s',
                opacity: 1
            }
        });

        const actorRect = actorEl.getBoundingClientRect();
        const targetRect = targetEl.getBoundingClientRect();

        document.body.appendChild(skillEmoji);
        skillEmoji.style.left = `${actorRect.left + window.scrollX}px`;
        skillEmoji.style.top = `${actorRect.top + window.scrollY}px`;

        // Fly to target
        requestAnimationFrame(() => {
            skillEmoji.style.transform = `translate(${targetRect.left - actorRect.left}px, ${targetRect.top - actorRect.top}px)`;
        });

        // Fade + remove
        setTimeout(() => {
            skillEmoji.style.opacity = 0;
            skillEmoji.remove();
        }, 1000);
    });

    // Actor strike animation (only once)
    actorEl.classList.add('strike');
    setTimeout(() => actorEl.classList.remove('strike'), 500);
}

// Helper to trigger outline flicker
function flickerOutline(targetEl, className, duration = 450) {
    if (!targetEl) return;
    if (targetEl.classList.contains(className)) {
        targetEl.classList.remove(className);
        void targetEl.offsetWidth;
    }
    targetEl.classList.add(className);
    setTimeout(() => targetEl.classList.remove(className), duration);
}

/**
 * @param {CombatEvent_DamageDealt} event - The damage dealt event object
 * @return {void}
 */
function animateDamageDealt(event) {
    const targetEl = document.getElementById(`actor-${event.target}`);
    if (!targetEl) {
        console.error(`Damage animation: target element not found (actor-${event.target})`);
        return;
    }

    flickerOutline(targetEl, 'flicker', 450);

    let damageVal = event.amount;
    const shown = Math.abs(damageVal);
    showFloatingNumber(targetEl, shown, 'damage');
}

/**
 * @param {CombatEvent_ResourceDrained} event - The resource drained event object
 * @return {void}
 */
function animateResourceDrained(event) {
    const targetId = `actor-${event.target}`;
    const targetEl = document.getElementById(targetId);
    if (!targetEl) {
        console.error(`Resource drain animation: target element not found (${targetId})`);
        return;
    }

    const amount = event.amount;

    if (amount < 0) {
        flickerOutline(targetEl, 'flicker', 450);
        showFloatingNumber(targetEl, Math.abs(amount), 'damage');
    } else if (amount > 0) {
        flickerOutline(targetEl, 'heal-flicker', 450);
        showFloatingNumber(targetEl, amount, 'heal');
    }
}

/**
 * @param {CombatEvent_Healed} event - The heal event object
 * @return {void}
 */
function animateHeal(event)
{
    let healAmount = event.amount;
    let elementId = `actor-${event.target}`;
    const domEl = document.getElementById(elementId);
    if(!domEl) {
        console.error('Heal animation: target element not found');
        return;
    }
    flickerOutline(domEl, 'heal-flicker', 450);
    showFloatingNumber(domEl, Math.abs(healAmount), 'heal');
}

/**
 * @param {CombatEvent_BuffApplied} event - The buff applied event object
 * @return {void}
 */
function animateBuffApplied(event) {
    // Find the target actor's statusEffects container
    const actorDiv = document.getElementById(`actor-${event.target}`);
    if (!actorDiv) {
        console.error(`Buff applied animation: target element not found (actor-${event.target})`);
        return;
    }

    const statusEffects = actorDiv.querySelector('.status-effects');
    if (!statusEffects) {
        console.error('Buff applied animation: status effects container not found');
        return;
    }

    // Add animation class
    statusEffects.classList.add('buff-animate');
    statusEffects.addEventListener('animationend', function handler() {
        statusEffects.classList.remove('buff-animate');
        statusEffects.removeEventListener('animationend', handler);
    });
}

// Helper to show floating numbers for damage / heal
function showFloatingNumber(actorEl, value, kind) {
    if (!actorEl) return;
    const num = document.createElement('div');
    num.textContent = value;
    num.classList.add('floating-number', kind === 'damage' ? 'damage-number' : 'heal-number');
    actorEl.appendChild(num);
    // remove after animation ends
    setTimeout(function() { if (num && num.parentNode) num.remove(); }, 1000);
}

export {
    animateSkillUsed,
    animateDamageDealt,
    animateResourceDrained,
    animateHeal,
    animateBuffApplied,
    showFloatingNumber
};
