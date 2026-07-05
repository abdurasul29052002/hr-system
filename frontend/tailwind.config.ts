import type { Config } from "tailwindcss";

// Tailwind v4 is configured primarily via CSS (see app/globals.css:
// @import "tailwindcss", @theme, @plugin). This file only declares content globs.
const config: Config = {
  content: [
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
};

export default config;
