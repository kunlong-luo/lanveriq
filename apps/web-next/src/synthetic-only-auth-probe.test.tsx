import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { SyntheticOnlyAuthProbe } from './synthetic-only-auth-probe';

// Deliberately never import this fixture from any normal App Router route.
// The opt-in manual workflow creates its disposable page after verify:routes.
describe('synthetic-only browser auth fixture is inert until human form submit', () => {
  it('renders a test-labeled browser form with no server fetch or credentials', () => {
    const html = renderToStaticMarkup(<SyntheticOnlyAuthProbe />);
    expect(html).toContain('合成登录浏览器联调');
    expect(html).toContain('禁止输入真实管理员凭据');
    expect(html).toContain('id="synthetic-auth-user"');
    expect(html).toContain('id="synthetic-auth-password"');
    expect(html).toContain('type="password"');
    expect(html).toContain('autocomplete="new-password"');
    expect(html).toContain('data-state="idle"');
    expect(html).toContain('disabled');
    expect(html).not.toContain('action="/api');
    expect(html).not.toContain('method="post"');
    expect(html).not.toContain('synthetic-owner');
    expect(html).not.toContain('test-password');
  });
});
