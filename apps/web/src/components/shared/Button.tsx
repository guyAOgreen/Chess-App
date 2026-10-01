import type { ComponentProps } from 'react';
import styles from './Button.module.css';

export type ButtonProps = ComponentProps<'button'>;

/**
 * The project's one button. It takes what a `<button>` takes; a `className`
 * adds layout and leaves the appearance alone.
 *
 * There are no variants because every button so far has the same weight. The
 * first one that needs to stand out is the time to add one.
 *
 * `type` defaults to `button` rather than the browser's `submit`, so a button
 * placed in a form never submits it by accident.
 */
export function Button({ className, type = 'button', ...props }: ButtonProps) {
  const classes = className ? `${styles.button} ${className}` : styles.button;
  return <button type={type} className={classes} {...props} />;
}
