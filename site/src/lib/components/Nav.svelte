<script lang="ts">
	import { asset, resolve } from '$app/paths';
	import Button from './Button.svelte';
	import Glyph from './Glyph.svelte';
	import { APK, REPO } from '#lib/site.ts';

	let scrolled = $state(false);
</script>

<svelte:window onscroll={() => (scrolled = window.scrollY > 8)} />

<header class:scrolled>
	<nav class="wrap" aria-label="Main">
		<a class="brand" href={resolve('/')}>
			<img src={asset('icon.svg')} alt="" width="28" height="28" />
			<span>OpenShortcuts</span>
		</a>
		<ul>
			<li class="hide-sm"><a href="#features">Features</a></li>
			<li class="hide-sm"><a href="#triggers">Triggers</a></li>
			<li class="hide-sm"><a href="#privacy">Privacy</a></li>
			<li>
				<a class="gh" href={REPO} rel="noopener" aria-label="OpenShortcuts on GitHub">
					<Glyph name="link" size={18} />
					<span class="hide-xs">GitHub</span>
				</a>
			</li>
			<li><Button href={APK} size="small">Download</Button></li>
		</ul>
	</nav>
</header>

<style>
	header {
		position: sticky;
		top: 0;
		z-index: 10;
		transition:
			background-color 200ms var(--ease-out),
			box-shadow 200ms var(--ease-out);
	}
	header.scrolled {
		background: var(--nav-glass);
		backdrop-filter: saturate(180%) blur(20px);
		-webkit-backdrop-filter: saturate(180%) blur(20px);
		box-shadow: 0 1px 0 var(--hairline);
	}
	@media (prefers-reduced-transparency: reduce) {
		header.scrolled {
			background: var(--bg);
			backdrop-filter: none;
			-webkit-backdrop-filter: none;
		}
	}
	@media (prefers-contrast: more) {
		header.scrolled {
			background: var(--bg);
			box-shadow: 0 1px 0 var(--ink3);
		}
	}
	nav {
		display: flex;
		align-items: center;
		justify-content: space-between;
		height: 60px;
	}
	.brand {
		display: flex;
		align-items: center;
		gap: 10px;
		font-weight: 650;
		font-size: 1.0625rem;
		text-decoration: none;
	}
	ul {
		display: flex;
		align-items: center;
		gap: clamp(12px, 2.4vw, 28px);
		list-style: none;
		margin: 0;
		padding: 0;
		font-size: 0.9375rem;
	}
	ul a:not(:global(.btn)) {
		color: var(--ink2);
		text-decoration: none;
		transition: color 150ms;
	}
	@media (hover: hover) and (pointer: fine) {
		ul a:not(:global(.btn)):hover {
			color: var(--ink);
		}
	}
	.gh {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		min-height: 44px;
	}
	@media (max-width: 760px) {
		.hide-sm {
			display: none;
		}
	}
	@media (max-width: 380px) {
		.hide-xs {
			display: none;
		}
	}
</style>
