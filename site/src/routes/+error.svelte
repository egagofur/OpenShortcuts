<script lang="ts">
	import { resolve } from '$app/paths';
	import Nav from '#lib/components/Nav.svelte';
	import Footer from '#lib/components/Footer.svelte';
	import Button from '#lib/components/Button.svelte';
</script>

<svelte:head>
	<title>Page not found - OpenShortcuts</title>
	<meta name="robots" content="noindex" />
</svelte:head>

<Nav />

<main class="wrap err">
	<p class="eyebrow">404</p>
	<h1>This page isn’t here.</h1>
	<p class="lede center">The link may be old, or the page moved.</p>
	<div class="cta">
		<Button href={resolve('/')}>Back to home</Button>
	</div>
</main>

<Footer />

<style>
	.err {
		text-align: center;
		padding-block: clamp(72px, 14vw, 140px);
	}
	h1 {
		font-size: clamp(2rem, 5vw, 3.25rem);
		font-weight: 700;
	}
	.center {
		margin-inline: auto;
	}
	.cta {
		margin-top: 28px;
	}
</style>
