<script lang="ts">
	import '#lib/tokens.css';
	import { asset, resolve } from '$app/paths';
	import { DESCRIPTION, REPO, SITE, TITLE } from '#lib/site.ts';
	import type { LayoutProps } from './$types';

	let { children }: LayoutProps = $props();

	const jsonLd = JSON.stringify({
		'@context': 'https://schema.org',
		'@type': 'SoftwareApplication',
		name: 'OpenShortcuts',
		operatingSystem: 'Android 10+',
		applicationCategory: 'UtilitiesApplication',
		description: DESCRIPTION,
		url: SITE,
		downloadUrl: `${REPO}/releases/latest`,
		license: 'https://opensource.org/licenses/MIT',
		isAccessibleForFree: true,
		offers: { '@type': 'Offer', price: '0', priceCurrency: 'USD' },
		image: `${SITE}og.png`
	});
</script>

<svelte:head>
	<title>{TITLE}</title>
	<meta name="description" content={DESCRIPTION} />
	<link rel="canonical" href={SITE} />
	<link rel="icon" href={asset('icon.svg')} type="image/svg+xml" />
	<link rel="apple-touch-icon" href={asset('apple-touch-icon.png')} />
	<meta name="theme-color" media="(prefers-color-scheme: light)" content="#f1f1f5" />
	<meta name="theme-color" media="(prefers-color-scheme: dark)" content="#0a0a0c" />

	<meta property="og:type" content="website" />
	<meta property="og:site_name" content="OpenShortcuts" />
	<meta property="og:title" content={TITLE} />
	<meta property="og:description" content={DESCRIPTION} />
	<meta property="og:url" content={SITE} />
	<meta property="og:image" content="{SITE}og.png" />
	<meta property="og:image:width" content="1600" />
	<meta property="og:image:height" content="900" />
	<meta name="twitter:card" content="summary_large_image" />
	<meta name="twitter:title" content={TITLE} />
	<meta name="twitter:description" content={DESCRIPTION} />
	<meta name="twitter:image" content="{SITE}og.png" />

	{@html `<script type="application/ld+json">${jsonLd}</script>`}
</svelte:head>

{@render children()}
