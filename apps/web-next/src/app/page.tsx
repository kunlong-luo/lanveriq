import { redirect } from 'next/navigation';

// No network, credentials, or data access at the root route.
export default function Home() { redirect('/overview'); }
