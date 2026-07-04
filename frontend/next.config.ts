import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: 'standalone',
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: process.env.NODE_ENV === 'production'
          ? 'http://hr-system-backend:8080/api/:path*'
          : 'http://localhost:8080/api/:path*',
      },
    ];
  },
};

export default nextConfig;
