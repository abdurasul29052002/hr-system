'use client';

import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeSanitize from 'rehype-sanitize';
import rehypeRaw from 'rehype-raw';

interface MarkdownRendererProps {
  content: string;
  className?: string;
}

export default function MarkdownRenderer({ content, className = '' }: MarkdownRendererProps) {
  return (
    <div className={`prose prose-sm max-w-none ${className}`}>
    <ReactMarkdown
      remarkPlugins={[remarkGfm]}
      rehypePlugins={[rehypeRaw, rehypeSanitize]}
      components={{
        // Custom rendering for mentions
        p: ({ children }) => {
          const text = String(children);
          if (text.includes('@')) {
            const parts = text.split(/(@[a-zA-Z0-9_]+)/g);
            return (
              <p>
                {parts.map((part, i) => {
                  if (part.startsWith('@')) {
                    return (
                      <span key={i} className="text-blue-600 font-medium bg-blue-50 px-1 rounded">
                        {part}
                      </span>
                    );
                  }
                  return <span key={i}>{part}</span>;
                })}
              </p>
            );
          }
          return <p>{children}</p>;
        },
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
