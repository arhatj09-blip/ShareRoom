/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,jsx}"],
  theme: {
    extend: {
      fontFamily: { sans: ["Inter", "ui-sans-serif", "system-ui", "sans-serif"] },
      colors: { ink: "#172033", brand: "#5b5ce2" },
      boxShadow: { card: "0 18px 50px rgba(37, 45, 93, .08)" }
    }
  },
  plugins: []
};
