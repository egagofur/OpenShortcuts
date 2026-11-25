<script lang="ts">
	import { asset, resolve } from '$app/paths';

	let { src, alt, eager = false }: { src: 'editor' | 'gallery' | 'sunset' | 'triggers' | 'catalog' | 'welcome'; alt: string; eager?: boolean } = $props();
</script>

<div class="phone">
	<img
		src={asset(`shots/${src}.webp`)}
		{alt}
		width="640"
		height="1406"
		loading={eager ? 'eager' : 'lazy'}
		fetchpriority={eager ? 'high' : undefined}
		decoding="async"
	/>
</div>

<style>
	.phone {
		width: 100%;
		aspect-ratio: 640 / 1406;
		padding: 6px;
		border-radius: 44px;
		background: #1a1a1d;
		box-shadow:
			inset 0 0 0 1.5px #3a3a40,
			0 30px 60px -20px rgb(0 0 0 / 0.35);
	}
	img {
		width: 100%;
		height: 100%;
		object-fit: cover;
		border-radius: 38px;
	}
</style>
