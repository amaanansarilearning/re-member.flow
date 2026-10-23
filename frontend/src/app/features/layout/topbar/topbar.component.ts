import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { User } from '../../../core/models';
import { initials } from '../../../core/utils/formatters';

@Component({
  selector: 'app-topbar',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './topbar.component.html',
  styleUrl: './topbar.component.scss'
})
export class TopbarComponent {
  @Input({ required: true }) user: User | null = null;
  @Input({ required: true }) viewTitle = '';
  @Input() dashboardBranchId: number | null = null;
  @Input() dashboardLocationLabel = '';
  @Input() todayDateLabel = '';

  @Output() logout = new EventEmitter<void>();

  readonly initials = initials;
}

