/** Guide §8 / FR-EMP-08 — client-side gates (server still enforces). */

export const DOCUMENT_MAX_BYTES = 10 * 1024 * 1024;
export const PHOTO_MAX_BYTES = 2 * 1024 * 1024;

export const DOCUMENT_ACCEPT = ".pdf,.jpg,.jpeg,.png,.docx";
export const PHOTO_ACCEPT = ".jpg,.jpeg,.png,image/jpeg,image/png";

const DOCUMENT_MIME = new Set([
  "application/pdf",
  "image/jpeg",
  "image/png",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
]);

const PHOTO_MIME = new Set(["image/jpeg", "image/png"]);

function extOf(name: string): string {
  const i = name.lastIndexOf(".");
  return i < 0 ? "" : name.slice(i).toLowerCase();
}

function normalizeMime(file: File): string {
  const raw = (file.type || "").trim().toLowerCase();
  if (raw === "image/jpg") return "image/jpeg";
  if (raw && raw !== "application/octet-stream") return raw;
  switch (extOf(file.name)) {
    case ".pdf":
      return "application/pdf";
    case ".jpg":
    case ".jpeg":
      return "image/jpeg";
    case ".png":
      return "image/png";
    case ".docx":
      return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    default:
      return raw;
  }
}

/** Returns an error message, or null when the file is acceptable. */
export function validateDocumentFile(file: File | null | undefined): string | null {
  if (!file) return "Choose a file to upload (PDF, JPG, PNG or DOCX, max 10 MB).";
  if (file.size <= 0) return "Uploaded file must not be empty.";
  if (file.size > DOCUMENT_MAX_BYTES) {
    return `File exceeds maximum allowed size of 10 MB (got ${(file.size / (1024 * 1024)).toFixed(1)} MB).`;
  }
  const mime = normalizeMime(file);
  const ext = extOf(file.name);
  const extOk = [".pdf", ".jpg", ".jpeg", ".png", ".docx"].includes(ext);
  if (!DOCUMENT_MIME.has(mime) && !extOk) {
    return `Unsupported content type '${file.type || ext || "unknown"}'. Allowed: PDF, JPG, PNG, DOCX (max 10 MB).`;
  }
  return null;
}

/** Profile photo — JPG/PNG only, max 2 MB. */
export function validatePhotoFile(file: File | null | undefined): string | null {
  if (!file) return "Choose a photo (JPG or PNG, max 2 MB).";
  if (file.size <= 0) return "Photo file must not be empty.";
  if (file.size > PHOTO_MAX_BYTES) {
    return `Photo exceeds maximum allowed size of 2 MB (got ${(file.size / (1024 * 1024)).toFixed(1)} MB).`;
  }
  const mime = normalizeMime(file);
  const ext = extOf(file.name);
  const extOk = [".jpg", ".jpeg", ".png"].includes(ext);
  if (!PHOTO_MIME.has(mime) && !extOk) {
    return `Unsupported photo type '${file.type || ext || "unknown"}'. Allowed: JPG, PNG (max 2 MB).`;
  }
  // Reject WebP/GIF/SVG even when browser reports image/*
  if (mime.startsWith("image/") && !PHOTO_MIME.has(mime)) {
    return "Unsupported photo type. Allowed: JPG, PNG (max 2 MB).";
  }
  return null;
}
