import { useState } from 'react';
import { Input, View } from '@tarojs/components';
import { useCustomers } from '@photogai/shared/hooks';
import PageContainer from '@/components/PageContainer';
import StateView from '@/components/StateView';
import CustomerCard from '@/components/CustomerCard';
import { toCustomerDetail } from '@/lib/nav';
import './index.scss';

/**
 * 客户列表页（tab）。
 * 顶部搜索框按关键字过滤（useCustomers(keyword) 走 shared 查询键，自动去重/缓存）。
 * 列表由 CustomerCard 渲染，三态交给 StateView。
 */
export default function CustomersPage() {
  const [keyword, setKeyword] = useState('');
  const { data, isLoading, error, refetch } = useCustomers(keyword);
  const list = data?.content ?? [];

  return (
    <PageContainer>
      <View className="customers-page">
        <View className="customers-page__search">
          <Input
            className="customers-page__input"
            placeholder="搜索客户姓名 / 微信 / 电话"
            value={keyword}
            onInput={(e) => setKeyword(e.detail.value)}
          />
        </View>

        <StateView
          loading={isLoading}
          error={error}
          empty={!isLoading && !error && list.length === 0}
          emptyText={keyword ? '未找到匹配客户' : '暂无客户'}
          onRetry={refetch}
        />

        {!isLoading && !error && list.length > 0 ? (
          <View className="customers-page__list">
            {list.map((customer) => (
              <CustomerCard
                key={customer.id}
                customer={customer}
                onClick={toCustomerDetail}
              />
            ))}
          </View>
        ) : null}
      </View>
    </PageContainer>
  );
}
