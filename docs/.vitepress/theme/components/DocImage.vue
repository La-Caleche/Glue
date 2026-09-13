<script setup lang="ts">
defineProps<{
  title: string;
  description: string;
  src?: string;
  alt?: string;
}>();
</script>

<template>
  <figure class="doc-image-placeholder">
    <img v-if="src" :src="src" :alt="alt ?? description" loading="lazy" decoding="async">
    <div v-else class="doc-image-canvas" aria-hidden="true">
      <svg aria-hidden="true" viewBox="0 0 64 48">
        <rect x="2" y="2" width="60" height="44" rx="5" />
        <circle cx="20" cy="16" r="4" />
        <path d="m9 39 14-14 9 8 7-6 16 12" />
      </svg>
      <span>IMAGE PLACEHOLDER</span>
    </div>
    <figcaption>
      <strong>{{ title }}</strong>
      <span>{{ description }}</span>
    </figcaption>
  </figure>
</template>

<style scoped>
.doc-image-placeholder {
  margin: 1.5rem 0 2rem;
  border: 1px solid var(--vp-c-border);
  border-radius: 12px;
  background: var(--vp-c-bg-soft);
  overflow: hidden;
}

.doc-image-placeholder img {
  display: block;
  width: 100%;
  height: auto;
  margin: 0;
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

.doc-image-canvas {
  display: grid;
  place-items: center;
  min-height: clamp(180px, 32vw, 300px);
  padding: 2rem;
  background:
    linear-gradient(135deg, transparent 49.5%, var(--vp-c-divider) 50%, transparent 50.5%),
    linear-gradient(45deg, transparent 49.5%, var(--vp-c-divider) 50%, transparent 50.5%),
    color-mix(in srgb, var(--vp-c-bg-alt) 82%, var(--vp-c-brand-soft));
  background-size: 28px 28px;
  color: var(--vp-c-text-3);
}

.doc-image-canvas svg {
  width: 64px;
  height: 48px;
  margin-bottom: 0.7rem;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2;
}

.doc-image-canvas span {
  font-family: var(--vp-font-family-mono);
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.doc-image-placeholder figcaption {
  display: grid;
  gap: 0.3rem;
  border-top: 1px solid var(--vp-c-border);
  padding: 0.9rem 1rem 1rem;
}

.doc-image-placeholder strong {
  color: var(--vp-c-text-1);
  font-size: 0.9rem;
}

.doc-image-placeholder figcaption span {
  color: var(--vp-c-text-2);
  font-size: 0.82rem;
  line-height: 1.55;
}
</style>
