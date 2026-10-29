import { z } from "zod";

/** Backend PageResponse<T> envelope (platform-common). */
export function pageResponseSchema<T extends z.ZodType>(item: T) {
  return z.object({
    content: z.array(item),
    page: z.number().int().nonnegative(),
    size: z.number().int().nonnegative(),
    totalElements: z.number().nonnegative(),
    totalPages: z.number().int().nonnegative(),
    sort: z.string().nullable().optional(),
  });
}

export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  sort?: string | null;
};
