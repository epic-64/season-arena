import { createElement} from "../src/utils.js";
import { updateActionLog } from "../src/eventLog.js";

describe('createElement', () => {
    beforeEach(() => {
        // Reset the document for each test
        document.body.innerHTML = '';
    });

    it('creates a div with text and class', () => {
        const el = createElement('div', { text: 'Hello', classes: ['greeting'] });
        expect(el.tagName).toBe('DIV');
        expect(el.textContent).toBe('Hello');
        expect(el.classList.contains('greeting')).toBe(true);
    });

    it('creates an element with id and styles', () => {
        const el = createElement('span', {
            id: 'my-span',
            styles: { color: 'red', fontWeight: 'bold' }
        });
        expect(el.tagName).toBe('SPAN');
        expect(el.id).toBe('my-span');
        expect(el.style.color).toBe('red');
        expect(el.style.fontWeight).toBe('bold');
    });
});

describe('updateActionLog', () => {
    beforeEach(() => {
        document.body.innerHTML = '<div id="action-log"></div>';
    });

    it('keeps only the last 50 messages in the log', () => {
        const logContainer = document.getElementById('action-log');
        logContainer.innerHTML = '';
        for (let i = 1; i <= 55; i++) {
            updateActionLog(`Message ${i}`);
        }
        expect(logContainer.children.length).toBe(50);
        expect(logContainer.firstChild.textContent).toBe('Message 6');
        expect(logContainer.lastChild.textContent).toBe('Message 55');
    });
});
