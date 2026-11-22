const MAILPIT = process.env.MAILPIT_URL ?? 'http://localhost:8025';

interface SearchResult { messages?: Array<{ ID: string }> }
interface Message { Text?: string }

/**
 * Waits for an email to the given address and returns the link in it whose path matches, for example
 * "verify-email" or "reset-password". Reads the real email the backend sent to the Mailpit catcher.
 */
export async function linkFromEmail(to: string, path: string, timeoutMs = 20_000): Promise<string> {
  const deadline = Date.now() + timeoutMs;
  const pattern = new RegExp(`https?://\\S+/${path}\\?token=[A-Za-z0-9_-]+`);
  while (Date.now() < deadline) {
    const search = await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${to}`)}`);
    if (search.ok) {
      const found = (await search.json()) as SearchResult;
      for (const m of found.messages ?? []) {
        const message = (await (await fetch(`${MAILPIT}/api/v1/message/${m.ID}`)).json()) as Message;
        const match = message.Text?.match(pattern);
        if (match) return match[0];
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`No "${path}" email arrived for ${to}`);
}
