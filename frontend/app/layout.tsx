import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "HR System",
  description: "Task management system for teams",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className="min-h-screen bg-gray-50 antialiased">{children}</body>
    </html>
  );
}
