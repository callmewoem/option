'use strict';

const config = require('../config');

const MESSAGES_URL = 'https://api.anthropic.com/v1/messages';
const ANTHROPIC_VERSION = '2023-06-01';
const MODEL = 'claude-haiku-4-5-20251001';
const SYSTEM_PROMPT =
  "You verify photo proof for a habit-tracking app that locks distracting apps until the " +
  "user's habits are done for the day. Be reasonably strict but fair: approve genuine, " +
  "current evidence the habit was just completed, and reject photos that are unrelated, " +
  "reused/old-looking, screenshots of other photos, or otherwise unconvincing. Always " +
  "reply with only the requested JSON object.";

class AnthropicNotConfiguredError extends Error {}

function textBlock(text) {
  return { type: 'text', text };
}

function imageBlock(base64, mediaType = 'image/jpeg') {
  return { type: 'image', source: { type: 'base64', media_type: mediaType, data: base64 } };
}

/**
 * Server-side twin of the Android app's old `AnthropicImageVerificationClient` --
 * moved here so the Claude API key lives only in this process's environment,
 * never inside the shipped APK. The app now sends the raw photo bytes to
 * POST /v1/verify-photo (see routes/verify.js) and this builds the exact same
 * request shape Anthropic expects, using the server's own ANTHROPIC_API_KEY.
 */
async function verifyPhoto({ habitName, description, exampleImageBase64, submittedImageBase64 }) {
  if (!config.anthropicApiKey) {
    throw new AnthropicNotConfiguredError('ANTHROPIC_API_KEY is not set on the server.');
  }

  const content = [];
  let rules = `Habit: "${habitName}".\n`;
  if (description && description.trim()) {
    rules += `What counts as done, in the user's own words: "${description.trim()}".\n`;
  }
  if (exampleImageBase64) {
    rules += 'An example photo of what "done" looks like is attached below, labeled EXAMPLE.\n';
  }
  if ((!description || !description.trim()) && !exampleImageBase64) {
    rules += 'The user gave no description, only judge against the habit name.\n';
  }
  content.push(textBlock(rules));

  if (exampleImageBase64) {
    content.push(textBlock('EXAMPLE photo:'));
    content.push(imageBlock(exampleImageBase64));
  }

  content.push(textBlock("SUBMITTED photo, just taken by the user as today's proof:"));
  content.push(imageBlock(submittedImageBase64));
  content.push(
    textBlock(
      'Decide if the submitted photo is genuine, current proof that the habit was completed. ' +
        'Reply with ONLY a compact JSON object, no other text: ' +
        '{"approved": true or false, "reasoning": "one short, friendly sentence"}.',
    ),
  );

  const response = await fetch(MESSAGES_URL, {
    method: 'POST',
    headers: {
      'x-api-key': config.anthropicApiKey,
      'anthropic-version': ANTHROPIC_VERSION,
      'content-type': 'application/json',
    },
    body: JSON.stringify({
      model: MODEL,
      max_tokens: 300,
      system: SYSTEM_PROMPT,
      messages: [{ role: 'user', content }],
    }),
  });

  const text = await response.text();
  if (!response.ok) {
    let message = `Verification failed (HTTP ${response.status}).`;
    try {
      const parsed = JSON.parse(text);
      if (parsed?.error?.message) message = parsed.error.message;
    } catch {
      // Body wasn't JSON -- keep the generic message above.
    }
    const error = new Error(message);
    error.status = response.status;
    throw error;
  }

  const body = JSON.parse(text);
  const answerText = body?.content?.[0]?.text;
  if (!answerText) throw new Error('The verification service returned no answer.');

  const start = answerText.indexOf('{');
  const end = answerText.lastIndexOf('}');
  if (start < 0 || end < start) throw new Error("Couldn't understand the verification result.");
  const verdict = JSON.parse(answerText.slice(start, end + 1));

  return {
    approved: Boolean(verdict.approved),
    reasoning: (verdict.reasoning && String(verdict.reasoning).trim()) || 'No reasoning given.',
  };
}

module.exports = { verifyPhoto, AnthropicNotConfiguredError };
