'use client';

import { Select } from '@base-ui/react/select';
import { Check, ChevronDown } from 'lucide-react';

/**
 * NXT-26: consistent, accessible Base UI select for current authorized-page
 * controls. Base UI owns keyboard navigation, option focus, portal positioning,
 * and ARIA semantics; no homemade dropdown logic or network requests.
 */
export type ConsoleSelectOption = Readonly<{ value: string; label: string }>;

export function ConsoleSelect({
  label, value, options, disabled, onChange,
}: Readonly<{
  label: string;
  value: string;
  options: readonly ConsoleSelectOption[];
  disabled: boolean;
  onChange: (value: string) => void;
}>) {
  return <Select.Root items={[...options]} value={value}
    disabled={disabled} onValueChange={next => {
      if (!disabled && typeof next === 'string') onChange(next);
    }}>
    <Select.Trigger type="button" aria-label={label} disabled={disabled}
      className="lv-select-trigger">
      <Select.Value className="lv-select-value" />
      <Select.Icon className="lv-select-caret">
        <ChevronDown size={16} aria-hidden="true" />
      </Select.Icon>
    </Select.Trigger>
    <Select.Portal>
      <Select.Positioner className="lv-select-positioner"
        sideOffset={7} align="start" alignItemWithTrigger={false}>
        <Select.Popup className="lv-select-popup">
          <Select.List className="lv-select-list">
            {options.map(option =>
              <Select.Item key={option.value} value={option.value}
                className="lv-select-item">
                <Select.ItemText>{option.label}</Select.ItemText>
                <Select.ItemIndicator className="lv-select-check">
                  <Check size={16} aria-hidden="true" />
                </Select.ItemIndicator>
              </Select.Item>)}
          </Select.List>
        </Select.Popup>
      </Select.Positioner>
    </Select.Portal>
  </Select.Root>;
}
