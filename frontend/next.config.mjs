/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  images: {
    remotePatterns: [
      { protocol: "https", hostname: "userpic.codeforces.org" },
      { protocol: "https", hostname: "codeforces.org" },
      { protocol: "https", hostname: "st.codeforces.com" },
    ],
  },
};

export default nextConfig;
