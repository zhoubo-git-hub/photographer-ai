import type { ReactNode } from 'react';
import { Text, View } from '@tarojs/components';
import './index.scss';

interface FieldRowProps {
  /** 左侧字段名。 */
  label: string;
  /** 右侧字段值；传 ReactNode 可放徽标等自定义内容。 */
  value?: ReactNode;
  /** 值为空时的占位符，与 shared 格式化函数的空值口径一致。 */
  placeholder?: string;
  /** 值区是否允许换行（长文本如报价建议、备注）。 */
  multiline?: boolean;
  /** 点击回调，传入时整行可点。 */
  onClick?: () => void;
}

/** 判断值是否视为"空"。注意 0 与 false 不算空。 */
function isEmptyValue(value: ReactNode): boolean {
  return value === null || value === undefined || value === '';
}

/**
 * 详情页「标签—值」行。
 * 空值统一显示 '-'，与 shared 的 formatAmount / formatDate 的空值占位保持一致。
 */
export default function FieldRow({
  label,
  value,
  placeholder = '-',
  multiline = false,
  onClick,
}: FieldRowProps) {
  const empty = isEmptyValue(value);
  return (
    <View
      className={`field-row ${multiline ? 'field-row--multiline' : ''} ${
        onClick ? 'field-row--clickable' : ''
      }`}
      onClick={onClick}
    >
      <Text className="field-row__label">{label}</Text>
      <View className="field-row__value">
        {empty ? <Text className="field-row__placeholder">{placeholder}</Text> : value}
      </View>
    </View>
  );
}
