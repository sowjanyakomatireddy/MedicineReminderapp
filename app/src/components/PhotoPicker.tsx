import { useEffect, useId, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { usePhoto } from '../api/photos';

/**
 * Pick or take a photo. The chosen file is kept until the form saves (so it works for new items too).
 * On phones, `capture` opens the camera straight away.
 */
export function PhotoPicker({
  label,
  existingPath,
  existingVersion,
  file,
  onFile,
  onRemove,
  className = '',
}: {
  label: string;
  existingPath?: string;
  existingVersion?: string;
  file: File | null;
  onFile: (f: File) => void;
  onRemove?: () => void;
  className?: string;
}) {
  const { t } = useTranslation();
  const id = useId();
  const existing = usePhoto(existingPath ?? null, existingVersion);
  const [preview, setPreview] = useState<string | null>(null);

  useEffect(() => {
    if (!file) return setPreview(null);
    const url = URL.createObjectURL(file);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  const shown = preview ?? existing;
  return (
    <div className={`flex flex-col gap-1 ${className}`}>
      <label
        htmlFor={id}
        className="flex h-full min-h-28 cursor-pointer flex-col items-center justify-center gap-1.5 overflow-hidden rounded-2xl border-2 border-dashed border-teal bg-white p-2 text-center text-[13px] font-semibold text-teal focus-within:outline focus-within:outline-3 focus-within:outline-teal"
      >
        {shown ? (
          <img src={shown} alt={label} className="h-24 w-full rounded-xl object-cover" />
        ) : (
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
            strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M4 8h3l2-3h6l2 3h3v11H4z" />
            <circle cx="12" cy="13" r="3.5" />
          </svg>
        )}
        <span>{shown ? t('common.changePhoto') : label}</span>
        <input
          id={id}
          type="file"
          accept="image/*"
          capture="environment"
          className="sr-only"
          onChange={(e) => e.target.files?.[0] && onFile(e.target.files[0])}
        />
      </label>
      {existing && !file && onRemove && (
        <button type="button" onClick={onRemove} className="min-h-11 text-sm font-semibold text-danger underline">
          {t('common.removePhoto')}
        </button>
      )}
    </div>
  );
}
