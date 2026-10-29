import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Self-contained server bundle for the Dockerfile.
  output: "standalone",
  poweredByHeader: false,
  reactStrictMode: true,
};

export default nextConfig;
