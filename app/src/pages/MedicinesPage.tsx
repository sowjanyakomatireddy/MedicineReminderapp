import { Link, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useFamily, usePeople } from '../api/family';
import { useMedicines, type Medicine, type Schedule } from '../api/medicines';
import { Alert } from '../components/ui';
import { PillIcon } from '../components/PillIcon';
import { PersonChips } from '../components/PersonChips';
import { Chip, FamilyPage, Loading } from '../components/layout';
import { dayName, formatTime } from '../components/format';

/** The Medicines tab: every medicine for one person, with its times. */
export function MedicinesPage() {
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();
  const family = useFamily();
  const people = usePeople();
  const personId = params.get('person') ?? people.data?.[0]?.id;
  const medicines = useMedicines(personId);
  const isEditor = family.data?.myRole === 'EDITOR';

  if (people.isPending || family.isPending) return <Loading />;
  if (people.isError) return <FamilyPage><Alert>{t('errors.loadFailed')}</Alert></FamilyPage>;
  const person = people.data.find((p) => p.id === personId);

  return (
    <FamilyPage>
      <h1 className="text-2xl font-bold">{t('medicines.title')}</h1>
      <PersonChips people={people.data} selected={personId} canAdd={isEditor}
        onSelect={(id) => setParams({ person: id }, { replace: true })} />
      {!person && <p className="rounded-2xl bg-white p-4">{t('home.noPeople')}</p>}
      {person && medicines.isPending && <Loading />}
      {medicines.isError && <Alert>{t('errors.loadFailed')}</Alert>}
      {person && medicines.data?.length === 0 && (
        <p className="rounded-[18px] bg-white p-4 text-muted">{t('home.noMedicines', { name: person.name })}</p>
      )}
      <ul className="flex flex-col gap-2.5">
        {medicines.data?.map((m) => <MedicineCard key={m.id} medicine={m} canEdit={isEditor} />)}
      </ul>
      {person && isEditor && (
        <Link to={`/medicines/new?person=${person.id}`}
          className="flex min-h-[52px] items-center justify-center gap-2 rounded-2xl bg-teal text-[17px] font-bold text-white">
          <span aria-hidden="true">+</span> {t('home.addMedicine')}
        </Link>
      )}
      <p className="text-sm text-muted">{t('common.disclaimer')}</p>
    </FamilyPage>
  );
}

function MedicineCard({ medicine: m, canEdit }: { medicine: Medicine; canEdit: boolean }) {
  const { t } = useTranslation();
  return (
    <li className="flex gap-3.5 rounded-[18px] bg-white px-4 py-3.5">
      <PillIcon shape={m.shape} color={m.color} photoPath={m.hasPhoto ? `/api/medicines/${m.id}/photo` : undefined}
        photoVersion={m.photoVersion} />
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <span className="text-[17px] font-semibold">
          {m.voiceName ? <><span>{m.voiceName}</span> · {m.name}</> : m.name}
        </span>
        <ul className="flex flex-col gap-0.5 text-sm text-muted">
          {m.schedules.map((s) => <li key={s.id}><ScheduleLine s={s} /></li>)}
        </ul>
        {m.notes && <p className="text-sm">{m.notes}</p>}
      </div>
      {canEdit && (
        <Link to={`/medicines/${m.id}`} className="flex min-h-11 items-center self-start text-sm font-semibold text-teal underline"
          aria-label={t('medicines.editNamed', { name: m.voiceName ?? m.name })}>
          {t('common.edit')}
        </Link>
      )}
    </li>
  );
}

function ScheduleLine({ s }: { s: Schedule }) {
  const { t, i18n } = useTranslation();
  const days = s.days.length === 7
    ? t('days.everyDay')
    : s.days.map((d) => dayName(d, i18n.resolvedLanguage)).join(', ');
  return (
    <span className="flex flex-wrap items-center gap-x-1.5">
      <span>{formatTime(s.time, i18n.resolvedLanguage)} · {t(`food.${s.food}`)} · {days}</span>
      {!s.active && <Chip>{t('medicines.paused')}</Chip>}
    </span>
  );
}
