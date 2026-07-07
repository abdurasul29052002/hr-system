import { ImageResponse } from "next/og";

// A branded 1200×630 preview used automatically for Open Graph + Twitter cards when the link is shared.
export const alt = "HR System — task management & Telegram bot for teams";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

export default function OpengraphImage() {
  return new ImageResponse(
    (
      <div
        style={{
          height: "100%",
          width: "100%",
          display: "flex",
          flexDirection: "column",
          justifyContent: "center",
          padding: "80px",
          background: "linear-gradient(135deg, #4f46e5 0%, #3730a3 100%)",
          fontFamily: "sans-serif",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: "22px" }}>
          <div
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              width: "88px",
              height: "88px",
              borderRadius: "20px",
              background: "#ffffff",
              color: "#4f46e5",
              fontSize: "40px",
              fontWeight: 800,
            }}
          >
            HR
          </div>
          <div style={{ display: "flex", color: "#ffffff", fontSize: "46px", fontWeight: 800 }}>HR System</div>
        </div>

        <div
          style={{
            display: "flex",
            marginTop: "44px",
            color: "#ffffff",
            fontSize: "66px",
            fontWeight: 800,
            lineHeight: 1.1,
            maxWidth: "960px",
          }}
        >
          Run your team’s work in one calm place
        </div>

        <div style={{ display: "flex", marginTop: "28px", color: "#e0e7ff", fontSize: "32px", maxWidth: "960px" }}>
          Kanban board · roles · live stats · monthly reports · a Telegram bot
        </div>

        <div style={{ display: "flex", marginTop: "48px", gap: "14px" }}>
          {["Free", "English", "Русский", "Oʻzbek"].map((s) => (
            <div
              key={s}
              style={{
                display: "flex",
                padding: "10px 24px",
                borderRadius: "999px",
                background: "rgba(255,255,255,0.16)",
                color: "#ffffff",
                fontSize: "26px",
              }}
            >
              {s}
            </div>
          ))}
        </div>
      </div>
    ),
    { ...size },
  );
}
