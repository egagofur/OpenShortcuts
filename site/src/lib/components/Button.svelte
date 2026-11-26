<script lang="ts">
	import type { Snippet } from 'svelte';

	let {
		href,
		variant = 'filled',
		size = 'large',
		children
	}: { href: string; variant?: 'filled' | 'outlined'; size?: 'large' | 'small'; children: Snippet } = $props();
</script>

<a {href} class="btn {variant} {size}" rel={href.startsWith('http') ? 'noopener' : undefined}>
	{@render children()}
</a>

<style>
	.btn {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		gap: 8px;
		border-radius: var(--radius-pill);
		font-weight: 600;
		text-decoration: none;
		white-space: nowrap;
		user-select: none;
		touch-action: manipulation;
		transition:
			scale 160ms var(--ease-out),
			background-color 160ms var(--ease-out);
	}
	.btn:active {
		scale: 0.97;
	}
	.large {
		min-height: 50px;
		padding: 0 24px;
		font-size: 1.0625rem;
	}
	.small {
		min-height: 36px;
		padding: 0 16px;
		font-size: 0.9375rem;
	}
	.filled {
		background: var(--accent);
		color: white;
	}
	.outlined {
		color: var(--accent);
		box-shadow: inset 0 0 0 1.5px var(--accent);
	}
	@media (hover: hover) and (pointer: fine) {
		.filled:hover {
			background: color-mix(in srgb, var(--accent) 88%, black);
		}
		.outlined:hover {
			background: var(--accent-wash);
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.btn:active {
			scale: none;
		}
	}
</style>
