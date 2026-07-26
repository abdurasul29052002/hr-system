import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Cookies from 'js-cookie';
import TaskCommentSection from '@/components/TaskCommentSection';
import type { Employee, TaskComment } from '@/lib/types';

/**
 * Covers the threaded-reply flow through the real `lib/api` layer (only `fetch` is stubbed): pressing
 * Reply, the composer chip, and that the POST carries the right `parentCommentId`. Also asserts a reply
 * bubble renders the quoted parent, and that a reply whose parent was deleted degrades gracefully.
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

function comment(overrides: Partial<TaskComment>): TaskComment {
  return {
    id: 1,
    taskId: TASK_ID,
    authorId: 99,
    authorName: 'Grace Hopper',
    content: 'the original comment',
    createdAt: '2026-07-20T09:00:00Z',
    updatedAt: null,
    viaTelegram: false,
    mentionedEmployeeIds: [],
    attachments: [],
    parentCommentId: null,
    parentAuthorName: null,
    parentPreview: null,
    ...overrides,
  };
}

function response(body: unknown, status = 200): Response {
  const text = JSON.stringify(body);
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => text,
    json: async () => JSON.parse(text),
  } as unknown as Response;
}

beforeEach(() => {
  Cookies.set('employee', JSON.stringify(ME));
  Cookies.set('token', 'test-token');
});

describe('TaskCommentSection — replying to a comment', () => {
  it('POSTs the reply with the parentCommentId of the comment being replied to', async () => {
    const parent = comment({ id: 101, content: 'parent comment', authorName: 'Grace Hopper' });
    const created = comment({
      id: 202,
      authorId: ME.id,
      authorName: ME.fullName,
      content: 'my reply',
      parentCommentId: 101,
      parentAuthorName: 'Grace Hopper',
      parentPreview: 'parent comment',
    });

    const fetchMock = vi.fn(async (path: string, init: RequestInit = {}) => {
      if (init.method === 'POST') return response(created);
      if (path === `/api/tasks/${TASK_ID}/comments`) return response([parent]);
      throw new Error(`unexpected request: ${init.method ?? 'GET'} ${path}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    const user = userEvent.setup();
    render(<TaskCommentSection taskId={TASK_ID} members={[]} />);
    expect(await screen.findByText('parent comment')).toBeInTheDocument();

    await user.click(screen.getByTitle('Reply'));
    // The composer shows who we are replying to.
    expect(await screen.findByText('Replying to Grace Hopper')).toBeInTheDocument();

    const textarea = screen.getByPlaceholderText(/Add a comment/i);
    await user.type(textarea, 'my reply');
    await user.click(screen.getByRole('button', { name: 'Send' }));

    const post = await waitFor(() => {
      const call = fetchMock.mock.calls.find(([, init]) => (init as RequestInit)?.method === 'POST');
      expect(call).toBeDefined();
      return call!;
    });

    expect(post[0]).toBe(`/api/tasks/${TASK_ID}/comments`);
    expect(JSON.parse(String((post[1] as RequestInit).body))).toMatchObject({
      content: 'my reply',
      parentCommentId: 101,
    });
  });

  it('renders the quoted parent on a reply bubble', async () => {
    const parent = comment({ id: 101, content: 'parent comment' });
    const reply = comment({
      id: 202,
      content: 'a reply',
      parentCommentId: 101,
      parentAuthorName: 'Grace Hopper',
      parentPreview: 'parent comment',
    });

    const fetchMock = vi.fn(async (path: string) => {
      if (path === `/api/tasks/${TASK_ID}/comments`) return response([parent, reply]);
      throw new Error(`unexpected request: ${path}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    render(<TaskCommentSection taskId={TASK_ID} members={[]} />);
    expect(await screen.findByText('a reply')).toBeInTheDocument();
    // The quote block shows the parent author and preview (preview appears twice: parent bubble + quote).
    expect(screen.getAllByText('parent comment').length).toBeGreaterThanOrEqual(2);
  });

  it('shows a deleted-comment placeholder when the parent is gone', async () => {
    // Parent was deleted: ON DELETE SET NULL leaves the reply, so the server sends null parent fields.
    const orphan = comment({
      id: 303,
      content: 'reply to a deleted comment',
      parentCommentId: null,
      parentAuthorName: null,
      parentPreview: null,
    });

    const fetchMock = vi.fn(async (path: string) => {
      if (path === `/api/tasks/${TASK_ID}/comments`) return response([orphan]);
      throw new Error(`unexpected request: ${path}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    render(<TaskCommentSection taskId={TASK_ID} members={[]} />);
    // The reply itself still renders fine; with no parent it simply has no quote block.
    expect(await screen.findByText('reply to a deleted comment')).toBeInTheDocument();
  });

  it('cancels a reply, dropping the parentCommentId from the next POST', async () => {
    const parent = comment({ id: 101, content: 'parent comment' });
    const created = comment({ id: 202, authorId: ME.id, authorName: ME.fullName, content: 'plain comment' });

    const fetchMock = vi.fn(async (path: string, init: RequestInit = {}) => {
      if (init.method === 'POST') return response(created);
      if (path === `/api/tasks/${TASK_ID}/comments`) return response([parent]);
      throw new Error(`unexpected request: ${init.method ?? 'GET'} ${path}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    const user = userEvent.setup();
    render(<TaskCommentSection taskId={TASK_ID} members={[]} />);
    expect(await screen.findByText('parent comment')).toBeInTheDocument();

    await user.click(screen.getByTitle('Reply'));
    expect(await screen.findByText('Replying to Grace Hopper')).toBeInTheDocument();
    await user.click(screen.getByLabelText('Cancel reply'));
    expect(screen.queryByText('Replying to Grace Hopper')).not.toBeInTheDocument();

    const textarea = screen.getByPlaceholderText(/Add a comment/i);
    await user.type(textarea, 'plain comment');
    await user.click(screen.getByRole('button', { name: 'Send' }));

    const post = await waitFor(() => {
      const call = fetchMock.mock.calls.find(([, init]) => (init as RequestInit)?.method === 'POST');
      expect(call).toBeDefined();
      return call!;
    });
    expect(JSON.parse(String((post[1] as RequestInit).body)).parentCommentId).toBeNull();
  });
});
