'use client';

import { Children, Fragment, type ReactNode } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeSanitize from 'rehype-sanitize';
import rehypeRaw from 'rehype-raw';

interface MarkdownRendererProps {
  content: string;
  className?: string;
}

/**
 * Highlights @mentions inside already-rendered markdown children.
 *
 * It walks the children and only splits STRING nodes on the mention pattern — non-string nodes (links
 * that remark-gfm auto-linked, bold, inline code, …) pass through untouched. The old code did
 * `String(children)` first, which turned any such element into the literal text "[object Object]"
 * whenever the paragraph also contained an '@'.
 */
function highlightMentions(children: ReactNode): ReactNode {
  return Children.map(children, (child, ci) => {
    if (typeof child !== 'string' || !child.includes('@')) {
      return child;
    }
    return child.split(/(@[a-zA-Z0-9_]+)/g).map((part, i) =>
      part.startsWith('@') ? (
        <span key={`${ci}-${i}`} className="rounded bg-blue-50 px-1 font-medium text-blue-600">
          {part}
        </span>
      ) : (
        <Fragment key={`${ci}-${i}`}>{part}</Fragment>
      ),
    );
  });
}

export default function MarkdownRenderer({ content, className = '' }: MarkdownRendererProps) {
  return (
    <div className={`prose prose-sm max-w-none ${className}`}>
    <ReactMarkdown
      remarkPlugins={[remarkGfm]}
      rehypePlugins={[rehypeRaw, rehypeSanitize]}
      components={{
        // Highlight @mentions without clobbering links or other inline elements in the same paragraph.
        p: ({ children }) => <p>{highlightMentions(children)}</p>,
        // Style code blocks
        code: ({ inline, children, ...props }: any) => {
          if (inline) {
            return (
              <code className="bg-gray-100 text-red-600 px-1 py-0.5 rounded text-sm" {...props}>
                {children}
              </code>
            );
          }
          return (
            <code className="block bg-gray-900 text-gray-100 p-3 rounded overflow-x-auto" {...props}>
              {children}
            </code>
          );
        },
        // Style links
        a: ({ children, href }) => (
          <a
            href={href}
            target="_blank"
            rel="noopener noreferrer"
            className="text-blue-600 hover:underline"
          >
            {children}
          </a>
        ),
        // Style blockquotes
        blockquote: ({ children }) => (
          <blockquote className="border-l-4 border-gray-300 pl-4 italic text-gray-600">
            {children}
          </blockquote>
        ),
        // Style lists
        ul: ({ children }) => <ul className="list-disc list-inside space-y-1">{children}</ul>,
        ol: ({ children }) => <ol className="list-decimal list-inside space-y-1">{children}</ol>,
        // Style headings
        h1: ({ children }) => <h1 className="text-xl font-bold mt-4 mb-2">{children}</h1>,
        h2: ({ children }) => <h2 className="text-lg font-bold mt-3 mb-2">{children}</h2>,
        h3: ({ children }) => <h3 className="text-base font-bold mt-2 mb-1">{children}</h3>,
      }}
    >
      {content}
    </ReactMarkdown>
    </div>
  );
}
