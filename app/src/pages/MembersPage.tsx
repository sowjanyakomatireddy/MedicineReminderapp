import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api, errorKey } from '../api/client';
import {
  createCode,
  keys,
  useFamily,
  useMembers,
  usePeople,
  type Member,
  type PairingCode,
  type Role,
} from '../api/family';
import { findLocale } from '../i18n/locales';
import { Alert, Button } from '../components/ui';
import { CodeCard } from '../components/CodeCard';
import { Avatar, BackLink, Chip, FamilyPage, Loading, SectionHeading } from '../components/layout';

export function MembersPage() {
  const { t } = useTranslation();
  const family = useFamily();
  const people = usePeople();
  const members = useMembers();

  if (family.isPending || people.isPending || members.isPending) return <Loading />;
  if (family.isError || people.isError || members.isError) {
    return (
      <FamilyPage>
        <Alert>{t('errors.loadFailed')}</Alert>
      </FamilyPage>
    );
  }
  if (!family.data) return null;
  const isEditor = family.data.myRole === 'EDITOR';

  return (
    <FamilyPage>
      <BackLink to="/">{t('nav.home')}</BackLink>
      <div className="flex flex-col gap-1.5">
        <h1 className="text-2xl font-bold">{t('members.title')}</h1>
        <p className="text-[15px] leading-relaxed text-[#34494E]">{t('members.intro')}</p>
      </div>

      <SectionHeading>{t('members.peopleHeading')}</SectionHeading>
      {people.data.length === 0 && <p className="rounded-[18px] bg-white p-4 text-muted">{t('members.noPeople')}</p>}
      <ul className="flex flex-col gap-2.5">
        {people.data.map((p) => {
          const lang = findLocale(p.voiceLanguage);
          return (
            <li key={p.id} className="flex items-center gap-3 rounded-[18px] bg-white px-4 py-3.5">
              <Avatar name={p.name} />
              <div className="flex min-w-0 flex-1 flex-col gap-1">
                <span className="text-[17px] font-bold">{p.name}</span>
                <span className="flex flex-wrap gap-1.5">
                  <Chip>
                    {t('members.voice')}{' '}
                    <span lang={p.voiceLanguage}>{lang?.nativeName ?? p.voiceLanguage}</span>
                  </Chip>
                  <Chip>{t('members.medicines', { count: p.medicineCount })}</Chip>
                  <Chip tone={p.linkedPhones > 0 ? 'green' : 'amber'}>
                    {p.linkedPhones > 0 ? t('members.phoneLinked') : t('members.noPhone')}
                  </Chip>
                </span>
              </div>
              <div className="flex flex-col items-end">
                <Link
                  to={`/family/people/${p.id}/setup`}
                  className="flex min-h-11 items-center text-sm font-bold text-teal"
                  aria-label={t('members.setUpPhoneFor', { name: p.name })}
                >
                  {t('members.setUpPhone')}&nbsp;<span aria-hidden="true">→</span>
                </Link>
                {isEditor && (
                  <Link
                    to={`/family/people/${p.id}`}
                    className="flex min-h-11 items-center text-sm font-semibold text-muted underline"
                    aria-label={t('members.editPerson', { name: p.name })}
                  >
                    {t('common.edit')}
                  </Link>
                )}
              </div>
            </li>
          );
        })}
      </ul>
      {isEditor && (
        <Link
          to="/family/people/new"
          className="flex min-h-[52px] items-center justify-center rounded-[18px] border-2 border-dashed border-teal font-bold text-teal"
        >
          {t('members.addPerson')}
        </Link>
      )}

      <SectionHeading>{t('members.familyHeading')}</SectionHeading>
      <ul className="flex flex-col rounded-[18px] bg-white px-4 py-1">
        {members.data.map((m, i) => (
          <MemberRow key={m.id} member={m} canManage={isEditor} last={i === members.data.length - 1} />
        ))}
      </ul>

      {isEditor && <InviteMember />}

      <p className="rounded-[14px] bg-[#DCEFEC] px-3.5 py-3 text-sm leading-relaxed text-teal-dark">
        {t('members.note')}
      </p>
    </FamilyPage>
  );
}

function MemberRow({ member, canManage, last }: { member: Member; canManage: boolean; last: boolean }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const navigate = useNavigate();
  const name = member.me ? t('members.you') : member.displayName;

  const changeRole = useMutation({
    mutationFn: (role: Role) =>
      api<void>(`/api/families/me/members/${member.id}`, { method: 'PATCH', body: JSON.stringify({ role }) }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: keys.members });
      void qc.invalidateQueries({ queryKey: keys.family });
    },
  });
  const remove = useMutation({
    mutationFn: () => api<void>(`/api/families/me/members/${member.id}`, { method: 'DELETE' }),
    onSuccess: () => {
      if (member.me) {
        qc.setQueryData(keys.family, null);
        navigate('/');
      } else {
        void qc.invalidateQueries({ queryKey: keys.members });
      }
    },
  });
  const error = changeRole.error ?? remove.error;

  function onRemove() {
    const question = member.me ? t('members.leaveConfirm') : t('members.removeConfirm', { name: member.displayName });
    if (window.confirm(question)) remove.mutate();
  }

  return (
    <li className={`flex flex-col gap-2 py-3 ${last ? '' : 'border-b border-[#E4E9E8]'}`}>
      <div className="flex items-center justify-between gap-3">
        <span className="font-semibold">{name}</span>
        {canManage && !member.me ? (
          <select
            aria-label={t('members.roleFor', { name: member.displayName })}
            value={member.role}
            onChange={(e) => changeRole.mutate(e.target.value as Role)}
            disabled={changeRole.isPending}
            className="min-h-11 rounded-lg border border-muted/40 bg-white px-2 text-sm"
          >
            <option value="EDITOR">{t('members.roleEDITOR')}</option>
            <option value="VIEWER">{t('members.roleVIEWER')}</option>
          </select>
        ) : (
          <span className="text-sm text-muted">{t(`members.role${member.role}`)}</span>
        )}
      </div>
      {(member.me || canManage) && (
        <button
          type="button"
          onClick={onRemove}
          disabled={remove.isPending}
          className="min-h-11 self-end text-sm font-semibold text-danger underline"
        >
          {member.me ? t('members.leave') : t('members.remove', { name: member.displayName })}
        </button>
      )}
      {error && <Alert>{t(errorKey(error, { 409: 'errors.lastEditor' }))}</Alert>}
    </li>
  );
}

function InviteMember() {
  const { t } = useTranslation();
  const [role, setRole] = useState<Role>('VIEWER');
  const [code, setCode] = useState<PairingCode | null>(null);
  const make = useMutation({ mutationFn: () => createCode('MEMBER', role), onSuccess: setCode });

  return (
    <section aria-labelledby="invite-heading" className="flex flex-col gap-3 rounded-[18px] bg-white p-4">
      <h2 id="invite-heading" className="text-lg font-bold">
        {t('members.inviteHeading')}
      </h2>
      <fieldset className="flex flex-col gap-2">
        <legend className="mb-1 font-medium">{t('members.inviteRole')}</legend>
        {(['VIEWER', 'EDITOR'] as const).map((r) => (
          <label key={r} className="flex min-h-12 items-center gap-3 rounded-xl border border-[#C9D3D2] px-3">
            <input
              type="radio"
              name="invite-role"
              value={r}
              checked={role === r}
              onChange={() => {
                setRole(r);
                setCode(null);
              }}
              className="size-5 accent-teal"
            />
            {t(`members.role${r}`)}
          </label>
        ))}
      </fieldset>
      {code ? (
        <CodeCard code={code} help={t('members.inviteHelp')} />
      ) : (
        <Button type="button" onClick={() => make.mutate()} disabled={make.isPending}>
          {t('members.makeInvite')}
        </Button>
      )}
      {make.error && <Alert>{t(errorKey(make.error))}</Alert>}
    </section>
  );
}
