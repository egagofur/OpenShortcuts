<script lang="ts">
	import Glyph from './Glyph.svelte';
	import { FAQS } from '#lib/site.ts';
</script>

<div class="faq card">
	{#each FAQS as f (f.q)}
		<details>
			<summary>{f.q}<span class="chev"><Glyph name="chevronDown" size={20} /></span></summary>
			<p>{f.a}</p>
		</details>
	{/each}
</div>

<style>
	.faq {
		margin-top: 32px;
		padding: 4px 24px;
	}
	details + details {
		border-top: 1px solid var(--hairline);
	}
	summary {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 16px;
		min-height: 60px;
		font-size: 1.0625rem;
		font-weight: 600;
		cursor: pointer;
		list-style: none;
		user-select: none;
	}
	summary::-webkit-details-marker {
		display: none;
	}
	.chev {
		color: var(--ink3);
		display: inline-flex;
		flex-shrink: 0;
		transition: rotate 220ms var(--ease-out);
	}
	details[open] .chev {
		rotate: 180deg;
	}
	p {
		color: var(--ink2);
		padding-bottom: 18px;
		max-width: 65ch;
	}
</style>
