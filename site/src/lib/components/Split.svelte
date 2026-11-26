<script lang="ts">
	import type { Snippet } from 'svelte';
	import Phone from './Phone.svelte';
	import { reveal } from '#lib/reveal.ts';

	let {
		id,
		eyebrow,
		title,
		shot,
		alt,
		flip = false,
		children
	}: { id?: string; eyebrow: string; title: string; shot: 'editor' | 'gallery' | 'sunset' | 'triggers' | 'catalog' | 'welcome' | 'workspace'; alt: string; flip?: boolean; children: Snippet } =
		$props();
</script>

<section {id} class="section wrap">
	<div class="split" class:flip>
		<div class="copy" use:reveal>
			<p class="eyebrow">{eyebrow}</p>
			<h2 class="h2">{title}</h2>
			{@render children()}
		</div>
		<div class="shot" use:reveal={80}>
			<Phone src={shot} {alt} />
		</div>
	</div>
</section>

<style>
	.split {
		display: grid;
		grid-template-columns: minmax(0, 1.1fr) minmax(0, 0.9fr);
		gap: clamp(32px, 6vw, 88px);
		align-items: center;
	}
	.flip .copy {
		order: 2;
	}
	.shot {
		width: min(320px, 100%);
		justify-self: center;
	}
	@media (max-width: 760px) {
		.split {
			grid-template-columns: 1fr;
		}
		.flip .copy {
			order: 0;
		}
		.shot {
			width: min(280px, 80%);
		}
	}
</style>
