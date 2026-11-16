import { useQuery } from '@tanstack/react-query';
import { api, fetchImage } from './client';

/** Shows a photo that needs the login token. `version` changes when the photo changes. */
export function usePhoto(path: string | null, version?: string) {
  return useQuery({
    queryKey: ['photo', path, version],
    queryFn: () => fetchImage(path!),
    enabled: !!path && !!version,
    staleTime: Infinity,
  }).data;
}

/**
 * Makes phone-camera photos small before upload (longest side 1024 px, JPEG).
 * If the browser cannot decode the image, the original file is sent and the server checks it.
 */
export async function shrinkImage(file: File, max = 1024): Promise<Blob> {
  try {
    const bitmap = await createImageBitmap(file);
    const scale = Math.min(1, max / Math.max(bitmap.width, bitmap.height));
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(bitmap.width * scale);
    canvas.height = Math.round(bitmap.height * scale);
    canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    return await new Promise<Blob>((resolve, reject) =>
      canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('encode'))), 'image/jpeg', 0.85),
    );
  } catch {
    return file;
  }
}

export async function uploadPhoto<T>(path: string, file: File): Promise<T> {
  const body = new FormData();
  body.append('photo', await shrinkImage(file), 'photo.jpg');
  return api<T>(path, { method: 'PUT', body });
}
