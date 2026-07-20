import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Cookies from 'js-cookie';
import TaskCommentSection from '@/components/TaskCommentSection';
import type { Employee, TaskComment } from '@/lib/types';

/**
 * Covers the comment edit flow end to end through the real `lib/api` layer: only `fetch` is stubbed,
 * so the request path, method and body these assertions see are the ones the browser would send.
 * That is deliberate — mocking `api.updateComment` would prove the component calls a function, but
 * would not catch a malformed URL inside it.
 */

const TASK_ID = 7;

const ME: Employee = {
  id: 42,
  fullName: 'Ada Lovelace',
  username: 'ada',
  phone: null,
  language: 'EN',
  admin: false,
  telegramLinked: false,
  telegramLinkCode: null,
  active: true,
  memberships: [],
};

const MY_COMMENT: TaskComment = {
  id: 101,
  taskId: TASK_ID,
  authorId: ME.id,
  authorName: ME.fullName,
  content: 'original text',
  createdAt: '2026-07-20T09:00:00Z',
  updatedAt: null,
  viaTelegram: false,
  mentionedEmployeeIds: [],
  attachments: [],
};

function response(body: unknown, status = 200): Response {
  const text = JSON.stringify(body);
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => text,
    json: async () => JSON.parse(text),
  } as unknown as Response;
}

/** Stubs fetch: the initial comment load always succeeds, the PUT is whatever the test asks for. */
function stubFetch(onPut: (body: unknown) => Response) {
  const fetchMock = vi.fn(async (path: string, init: RequestInit = {}) => {
    if (init.method === 'PUT') return onPut(JSON.parse(String(init.body)));
    if (path === `/api/tasks/${TASK_ID}/comments`) return response([MY_COMMENT]);
    throw new Error(`unexpected request: ${init.method ?? 'GET'} ${path}`);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

async function openEditor() {
  const user = userEvent.setup();
  render(<TaskCommentSection taskId={TASK_ID} members={[]} />);
  expect(await screen.findByText('original text')).toBeInTheDocument();

  await user.click(screen.getByTitle('Edit'));
  const textarea = await screen.findByDisplayValue('original text');
  return { user, textarea };
}

beforeEach(() => {
  Cookies.set('employee', JSON.stringify(ME));
  Cookies.set('token', 'test-token');
});

describe('TaskCommentSection — editing a comment', () => {
  it('PUTs to /api/comments/:id and replaces the comment with the server response', async () => {
    const fetchMock = stubFetch((body) =>
      response({ ...MY_COMMENT, content: (body as { content: string }).content, updatedAt: '2026-07-20T10:00:00Z' }),
    );
    const { user, textarea } = await openEditor();

    await user.clear(textarea);
    await user.type(textarea, 'edited text');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    const put = await waitFor(() => {
      const call = fetchMock.mock.calls.find(([, init]) => (init as RequestInit)?.method === 'PUT');
      expect(call).toBeDefined();
      return call!;
    });

    // The exact path matters: a template literal built with backslashes collapses to a relative
    // "apicomments${id}" string that silently never reaches the backend.
    expect(put[0]).toBe('/api/comments/101');
    expect(JSON.parse(String((put[1] as RequestInit).body))).toEqual({ content: 'edited text' });

    // Editor closes and the server's version — not the local draft — is what renders.
    expect(await screen.findByText('edited text')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
    expect(screen.getByText('(edited)')).toBeInTheDocument();
  });

  it('trims whitespace off the saved content', async () => {
    const fetchMock = stubFetch((body) => response({ ...MY_COMMENT, content: (body as { content: string }).content }));
    const { user, textarea } = await openEditor();

    await user.clear(textarea);
    await user.type(textarea, '   padded   ');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => {
      const put = fetchMock.mock.calls.find(([, init]) => (init as RequestInit)?.method === 'PUT');
      expect(put).toBeDefined();
      expect(JSON.parse(String((put![1] as RequestInit).body))).toEqual({ content: 'padded' });
    });
  });

  it('keeps the editor open and reports the error when the server rejects the edit', async () => {
    const alert = vi.spyOn(window, 'alert').mockImplementation(() => {});
    stubFetch(() => response({ message: 'Only the author can edit this comment' }, 403));
    const { user, textarea } = await openEditor();

    await user.clear(textarea);
    await user.type(textarea, 'edited text');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(alert).toHaveBeenCalledWith('Only the author can edit this comment'));
    // Still editing, so the user's draft is not lost.
    expect(screen.getByDisplayValue('edited text')).toBeInTheDocument();
  });

  it('cannot save an empty comment', async () => {
    const fetchMock = stubFetch(() => response(MY_COMMENT));
    const { user, textarea } = await openEditor();

    await user.clear(textarea);

    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(fetchMock.mock.calls.some(([, init]) => (init as RequestInit)?.method === 'PUT')).toBe(false);
  });

  it('discards the draft on cancel without calling the server', async () => {
    const fetchMock = stubFetch(() => response(MY_COMMENT));
    const { user, textarea } = await openEditor();

    await user.clear(textarea);
    await user.type(textarea, 'abandoned draft');
    await user.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(await screen.findByText('original text')).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([, init]) => (init as RequestInit)?.method === 'PUT')).toBe(false);
  });
});
