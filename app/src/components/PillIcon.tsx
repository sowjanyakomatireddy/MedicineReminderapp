import { useId } from 'react';
import type { PillColour, Shape } from '../api/medicines';
import { usePhoto } from '../api/photos';

const FILL: Record<PillColour, string> = {
  WHITE: '#FFFFFF',
  YELLOW: '#F2C230',
  ORANGE: '#F28C28',
  PINK: '#F4A6C0',
  RED: '#C8364B',
  BLUE: '#4A7BD0',
  GREEN: '#4FA36B',
  BROWN: '#8B5A2B',
  MIXED: '#C8364B',
};

/**
 * A drawing of the tablet from its shape and colour, or the photo if there is one.
 * Decorative: the medicine's name (and shape/colour words where relevant) are always shown as text.
 */
export function PillIcon({
  shape,
  color,
  photoPath,
  photoVersion,
  size = 44,
}: {
  shape?: Shape;
  color?: PillColour;
  photoPath?: string;
  photoVersion?: string;
  size?: number;
}) {
  const photo = usePhoto(photoPath ?? null, photoVersion);
  const clipId = useId();
  if (photo) {
    return <img src={photo} alt="" className="flex-none rounded-full object-cover" style={{ width: size, height: size }} />;
  }
  const fill = FILL[color ?? 'WHITE'];
  const stroke = '#14262B';
  return (
    <span
      aria-hidden="true"
      className="flex flex-none items-center justify-center rounded-full bg-[#DCEFEC]"
      style={{ width: size, height: size }}
    >
      <svg width={size * 0.7} height={size * 0.7} viewBox="0 0 32 32">
        {shape === 'CAPSULE' ? (
          <g>
            <clipPath id={clipId}>
              <rect x="2" y="10" width="28" height="12" rx="6" />
            </clipPath>
            <rect x="2" y="10" width="28" height="12" rx="6" fill="#FFFFFF" />
            <rect x="2" y="10" width="14" height="12" fill={fill} clipPath={`url(#${clipId})`} />
            <rect x="2" y="10" width="28" height="12" rx="6" fill="none" stroke={stroke} strokeWidth="2" />
          </g>
        ) : shape === 'OVAL' ? (
          <ellipse cx="16" cy="16" rx="13" ry="8" fill={fill} stroke={stroke} strokeWidth="2" />
        ) : shape === 'OTHER' ? (
          <rect x="6" y="6" width="20" height="20" rx="5" fill={fill} stroke={stroke} strokeWidth="2" />
        ) : (
          <g>
            <circle cx="16" cy="16" r="12" fill={color === 'MIXED' ? '#FFFFFF' : fill} stroke={stroke} strokeWidth="2" />
            {color === 'MIXED' && <path d="M16 4a12 12 0 0 1 0 24z" fill={fill} />}
            <path d="M16 6v20" stroke={stroke} strokeWidth="1.5" opacity="0.35" />
          </g>
        )}
      </svg>
    </span>
  );
}
