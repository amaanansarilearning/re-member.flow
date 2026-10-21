import { Component, EventEmitter, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-cookie-banner',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './cookie-banner.component.html',
  styleUrl: './cookie-banner.component.scss'
})
export class CookieBannerComponent {
  @Output() dismiss = new EventEmitter<void>();
  @Output() viewLegal = new EventEmitter<'terms' | 'privacy' | 'disclaimer' | 'acceptable-use'>();
}

