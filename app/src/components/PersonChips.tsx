import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { Person } from '../api/family';

/** The "FAMILY" row from the prototype: pick whose day or medicines to see. */
export function PersonChips({
  people,
  selected,
  onSelect,
  canAdd,
}: {
  people: Person[];
  selected?: string;
  onSelect: (id: string) => void;
  canAdd: boolean;
}) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col gap-3">
      <h2 className="text-sm font-semibold tracking-[0.04em] text-muted uppercase">{t('home.familyLabel')}</h2>
      <ul className="flex flex-wrap gap-2">
        {people.map((p) => {
          const on = p.id === selected;
          return (
            <li key={p.id}>
              <button
                type="button"
                aria-pressed={on}
                onClick={() => onSelect(p.id)}
                className={`min-h-11 rounded-full px-[18px] text-base font-semibold ${on ? 'bg-teal text-white' : 'border border-[#C9D3D2] bg-white text-ink'}`}
              >
                {p.name}
              </button>
            </li>
          );
        })}
        {canAdd && (
          <li>
            <Link
              to="/family/people/new"
              className="flex min-h-11 items-center rounded-full border border-dashed border-teal bg-white px-4 text-base font-semibold text-teal"
            >
              {t('home.addPerson')}
            </Link>
          </li>
        )}
      </ul>
    </div>
  );
}
